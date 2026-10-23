package com.example.smartirrigation.ui;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.AiAction;
import com.example.smartirrigation.model.AiConfirmData;
import com.example.smartirrigation.model.AiConfirmRequest;
import com.example.smartirrigation.model.ChatData;
import com.example.smartirrigation.model.ChatRequest;
import com.example.smartirrigation.model.ChatSession;
import com.example.smartirrigation.model.ChatTurn;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话（只读）：App 侧三步 —— 登录拿 token（全局拦截器注入）→ 取 device_id → 调 /api/app/ai/chat。
 * 上下文由 App 维护，随请求带 history（服务端只取最后 6 条、每条 ≤500 字）。
 */
public class ChatActivity extends BaseActivity implements ApiCallback<ChatData> {

    /** 从 AI 页快捷提问进入时，自动带上并发送的问题 */
    public static final String EXTRA_QUESTION = "extra_question";
    /** 从「历史对话」进入时指定的会话 id；不带则是全新会话 */
    public static final String EXTRA_SESSION_ID = "extra_session_id";

    /** 只传最近 6 条（3 轮问答）：服务端最多也吃 8 条，传多了纯浪费流量 */
    private static final int MAX_HISTORY_TURNS = 6;
    private static final int MAX_QUESTION_LENGTH = 500;
    /** 单条历史同样按服务端上限截断 */
    private static final int MAX_TURN_LENGTH = 500;
    /** 本地保留的对话条数（历史对话点进去要能看全，请求仍只带最近 6 条） */
    private static final int MAX_LOCAL_TURNS = 40;
    /** 超过该轮数提示用户可新开对话（服务端虽会裁剪，但长对话参考价值下降） */
    private static final int LONG_CHAT_ROUNDS = 10;

    private static final String GREETING =
            "你好，我是灌溉助手。可以结合这台设备的实时数据回答问题，也可以帮你生成浇水、计划、分区等操作提案。\n\n"
                    + "注意：操作不会自动执行，需要你在卡片上点「确认执行」。";

    private static final String NOTE_CONFIRM = "操作提案 · 确认后由服务器执行";

    /** 提示框是否已被用户收起（进程内记忆，重进不再打扰） */
    private static boolean bannerDismissed;

    private ViewGroup chatRoot;
    private View chatBanner;
    private RecyclerView rvChat;
    private EditText etQuestion;
    private ImageButton btnSend;
    private MaterialToolbar toolbar;
    private ChatAdapter adapter;

    private final List<ChatAdapter.Msg> messages = new ArrayList<>();
    private final List<ChatTurn> turns = new ArrayList<>();

    private boolean sending;
    private int pendingIndex = -1;
    private String pendingQuestion;
    /** 当前会话 id（本地多会话，服务端无状态） */
    private String sessionId;
    /** 点过“新对话”且尚未成功发送：下次请求带 new_session=true 做双保险 */
    private boolean freshSession;
    /** 已提示过“对话较长”的轮数，避免重复打扰 */
    private int warnedRounds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        setupPage("AI 对话", "云端大模型 · 带上下文 · 只读", 0);

        // 白底标题栏配浅色状态栏，避免深绿状态栏压在白栏上
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.white));
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        chatRoot = findViewById(R.id.chatRoot);
        chatBanner = findViewById(R.id.chatBanner);
        rvChat = findViewById(R.id.rvChat);
        etQuestion = findViewById(R.id.etQuestion);
        btnSend = findViewById(R.id.btnSend);

        toolbar = findViewById(R.id.toolbar);
        findViewById(R.id.btnNewChat).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newChat();
            }
        });

        if (bannerDismissed) {
            chatBanner.setVisibility(View.GONE);
        } else {
            findViewById(R.id.btnDismissBanner).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissBanner();
                }
            });
        }

        adapter = new ChatAdapter(new ChatAdapter.ActionListener() {
            @Override
            public void onAction(int position, AiAction action, boolean confirm) {
                confirmAction(position, action, confirm);
            }
        });
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(false);
        rvChat.setLayoutManager(manager);
        rvChat.setAdapter(adapter);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (TextUtils.isEmpty(sessionId)) {
            sessionId = newSessionId();
        }
        restoreConversation();

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
        etQuestion.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    send();
                    return true;
                }
                return false;
            }
        });

        bindQuickAsk(R.id.chipAsk1);
        bindQuickAsk(R.id.chipAsk2);
        bindQuickAsk(R.id.chipAsk3);

        String preset = getIntent().getStringExtra(EXTRA_QUESTION);
        if (!TextUtils.isEmpty(preset)) {
            etQuestion.setText(preset);
            etQuestion.setSelection(preset.length());
            send();
        }
    }

    /** 收起顶部提示框：高度平滑过渡，收起后列表自动补位，不留空白 */
    private void dismissBanner() {
        bannerDismissed = true;
        if (chatRoot == null || chatBanner == null) {
            return;
        }
        AutoTransition transition = new AutoTransition();
        transition.setDuration(180L);
        TransitionManager.beginDelayedTransition(chatRoot, transition);
        chatBanner.setVisibility(View.GONE);
    }

    private void bindQuickAsk(int chipId) {
        Chip chip = findViewById(chipId);
        if (chip == null) {
            return;
        }
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etQuestion.setText(chip.getText());
                etQuestion.setSelection(etQuestion.getText().length());
                send();
            }
        });
    }

    /** 恢复上次对话；没有历史时给一段开场白 */
    private void restoreConversation() {
        messages.clear();
        turns.clear();
        // 只恢复指定会话；从「开始对话」进来则是一个全新会话
        ChatSession session = SessionManager.get().findChatSession(sessionId);
        if (session != null && session.turns != null) {
            turns.addAll(session.turns);
        } else {
            freshSession = true;
        }

        for (ChatTurn turn : turns) {
            int type = "assistant".equalsIgnoreCase(turn.role)
                    ? ChatAdapter.TYPE_AI : ChatAdapter.TYPE_USER;
            messages.add(new ChatAdapter.Msg(type, turn.content));
        }
        if (messages.isEmpty()) {
            messages.add(new ChatAdapter.Msg(ChatAdapter.TYPE_AI, GREETING,
                    "操作需你确认后才会执行，不会自动下发"));
        }
        adapter.submit(messages);
        scrollToBottom();
        updateTitle();
    }

    /** 标题显示当前轮数，长对话时一眼可见“该换新会话了” */
    private void updateTitle() {
        if (toolbar == null) {
            return;
        }
        int rounds = turns.size() / 2;
        toolbar.setTitle(rounds > 0 ? "AI 对话 · " + rounds + " 轮" : "AI 对话");
    }

    /**
     * 新对话：服务端本来就无状态，清空本地消息与 history 即可；
     * 同时置 freshSession，下一次请求附带 new_session=true 作为双保险。
     */
    private void newChat() {
        if (sending) {
            toast("等这条回答结束再开新对话");
            return;
        }
        messages.clear();
        turns.clear();
        SessionManager.get().clearChatHistory();
        // 换一个新会话 id，旧会话仍保留在「历史对话」里
        sessionId = newSessionId();
        messages.add(new ChatAdapter.Msg(ChatAdapter.TYPE_AI, GREETING,
                "操作需你确认后才会执行，不会自动下发"));
        adapter.submit(messages);
        freshSession = true;
        warnedRounds = 0;
        updateTitle();
        etQuestion.setText("");
        etQuestion.requestFocus();
        toast("已开启新对话，历史上下文已清空");
    }

    private void send() {
        String question = etQuestion.getText().toString().trim();
        if (TextUtils.isEmpty(question)) {
            toast("先输入你想问的问题");
            return;
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            toast("问题请控制在 500 字以内");
            return;
        }
        if (isDemo()) {
            toast("离线演示模式没有真实设备数据，请登录后再和 AI 对话");
            return;
        }
        long deviceId = deviceId();
        if (deviceId <= 0) {
            toast("请先绑定并选择设备");
            return;
        }
        if (sending) {
            toast("上一条还在回答中，请稍候");
            return;
        }

        sending = true;
        btnSend.setEnabled(false);
        etQuestion.setText("");
        hideKeyboard();

        pendingQuestion = question;
        messages.add(new ChatAdapter.Msg(ChatAdapter.TYPE_USER, question));
        ChatAdapter.Msg thinking = new ChatAdapter.Msg(ChatAdapter.TYPE_AI, "思考中…",
                "正在结合设备实时数据");
        thinking.pending = true;
        messages.add(thinking);
        adapter.submit(messages);
        pendingIndex = adapter.lastIndex();
        scrollToBottom();

        NetworkModule.enqueue(NetworkModule.chatApi()
                .chat(new ChatRequest(deviceId, question,
                        freshSession ? new ArrayList<ChatTurn>() : buildHistory(), freshSession)), this);
    }

    /** 带上最近几轮作为上下文（服务端仍会截断到最后 6 条） */
    private List<ChatTurn> buildHistory() {
        List<ChatTurn> history = new ArrayList<>();
        int from = Math.max(0, turns.size() - MAX_HISTORY_TURNS);
        for (int i = from; i < turns.size(); i++) {
            history.add(turns.get(i));
        }
        // 首轮也传空数组，避免部分服务端对 null 的历史处理不一致
        return history;
    }

    /** 追加一条历史（单条截断到 500 字，整体只保留最近 6 条），并落盘 */
    private void addTurn(String role, String content) {
        String text = content == null ? "" : content.trim();
        if (text.length() > MAX_TURN_LENGTH) {
            text = text.substring(0, MAX_TURN_LENGTH);
        }
        turns.add(new ChatTurn(role, text));
        // 本地多留一些（历史对话要能回看），发给模型的仍只有最近 6 条
        while (turns.size() > MAX_LOCAL_TURNS) {
            turns.remove(0);
        }
        // 同步到「历史对话」列表（置顶 + 刷新标题/时间）
        SessionManager.get().upsertChatSession(sessionId, turns);
    }

    private String newSessionId() {
        return "s" + System.currentTimeMillis();
    }

    @Override
    public void onSuccess(ChatData data) {
        sending = false;
        btnSend.setEnabled(true);
        if (isFinishing()) {
            return;
        }
        String reply = data == null || TextUtils.isEmpty(data.reply)
                ? "服务端没有返回内容，请稍后再试。" : data.reply;
        boolean hasAction = data != null && data.action != null
                && !TextUtils.isEmpty(data.action.summary);
        String note;
        if (hasAction) {
            note = NOTE_CONFIRM;
        } else if (data != null && data.fromRule()) {
            note = "AI 暂不可用，以上为设备数据摘要";
        } else {
            note = data == null ? "只读回答" : "只读回答 · 服务端耗时 " + data.latency_ms + " ms";
        }

        ChatAdapter.Msg answer = new ChatAdapter.Msg(ChatAdapter.TYPE_AI, reply, note);
        if (hasAction) {
            answer.action = data.action;
            answer.actionState = "pending";
        }
        if (pendingIndex >= 0 && pendingIndex <= adapter.lastIndex()) {
            adapter.update(pendingIndex, answer);
            if (pendingIndex < messages.size()) {
                messages.set(pendingIndex, answer);
            }
        } else {
            messages.add(answer);
            adapter.submit(messages);
        }
        pendingIndex = -1;

        // 记录本轮上下文并持久化（服务端不存会话，只吃 App 传来的 history）
        addTurn("user", pendingQuestion == null ? "" : pendingQuestion);
        addTurn("assistant", reply);
        // 注意：待确认卡片只是 UI 状态，不进 history；只有已执行结果才回填
        freshSession = false;
        updateTitle();
        int rounds = turns.size() / 2;
        if (rounds >= LONG_CHAT_ROUNDS && rounds > warnedRounds) {
            warnedRounds = rounds;
            toast("对话较长，可点右上角「新对话」重新开始");
        }

        // metrics 回填首页缓存，省一次 status 请求
        if (data != null) {
            SessionManager.get().applyChatMetrics(data.metrics);
        }
        scrollToBottom();
    }

    @Override
    public void onError(String message) {
        sending = false;
        btnSend.setEnabled(true);
        if (isFinishing()) {
            return;
        }
        ChatAdapter.Msg failed = new ChatAdapter.Msg(ChatAdapter.TYPE_AI, friendlyError(message),
                "也可以到「远程控制」页手动操作水泵");
        if (pendingIndex >= 0 && pendingIndex <= adapter.lastIndex()) {
            adapter.update(pendingIndex, failed);
            if (pendingIndex < messages.size()) {
                messages.set(pendingIndex, failed);
            }
        } else {
            messages.add(failed);
            adapter.submit(messages);
        }
        pendingIndex = -1;
        scrollToBottom();
    }

    private String friendlyError(String message) {
        String msg = message == null ? "" : message;
        if (msg.contains("429") || msg.contains("频繁")) {
            return "提问太频繁了。同一台设备 60 秒内最多 10 次，请稍后再试。";
        }
        if (msg.contains("登录") || msg.contains("401")) {
            return "登录已过期，请重新登录后再对话。";
        }
        if (msg.contains("400") || msg.contains("参数")) {
            return "问题内容不合法，换个说法再试试。";
        }
        return "这次没答上来：" + (msg.isEmpty() ? "服务端无响应" : msg) + "\n稍后再试一次即可。";
    }

    // ================= AI 操作提案：用户确认后才提交 =================

    private void confirmAction(final int position, final AiAction action, final boolean confirm) {
        if (action == null || position < 0 || position >= messages.size()) {
            return;
        }
        if (isDemo()) {
            toast("离线演示模式不能执行操作，请登录后使用");
            return;
        }
        ChatAdapter.Msg msg = messages.get(position);
        if (!"pending".equals(msg.actionState) && !"failed".equals(msg.actionState)) {
            return;
        }
        msg.actionState = "loading";
        msg.actionBusy = confirm;
        msg.actionResult = confirm ? "正在提交确认…" : "正在取消…";
        adapter.update(position, msg);

        NetworkModule.enqueue(NetworkModule.api().aiConfirm(
                        new AiConfirmRequest(action.id, confirm ? "confirm" : "cancel")),
                new ApiCallback<AiConfirmData>() {
                    @Override
                    public void onSuccess(AiConfirmData data) {
                        ChatAdapter.Msg current = messageAt(position);
                        if (current == null) {
                            return;
                        }
                        current.actionState = "done";
                        current.actionBusy = false;
                        current.actionResult = data != null && !TextUtils.isEmpty(data.message)
                                ? data.message : (confirm ? "已执行" : "已取消");
                        if (confirm) {
                            int seconds = pumpDurationSec(action);
                            if (seconds > 0) {
                                current.countdownSec = seconds;
                                current.countdownEndAt = SystemClock.elapsedRealtime() + seconds * 1000L;
                            }
                        }
                        adapter.update(position, current);
                        toast(current.actionResult);
                        // 执行结果回填为一条 assistant 消息，模型下一轮才知道事情已经做了
                        if (confirm) {
                            recordExecuted(action, current.actionResult);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        ChatAdapter.Msg current = messageAt(position);
                        if (current == null) {
                            return;
                        }
                        // 409 = 已被处理过（重复点击/别处已确认），按已完成展示
                        boolean handled = message != null
                                && (message.contains("409") || message.contains("已处理"));
                        current.actionState = handled ? "done" : "failed";
                        current.actionBusy = false;
                        current.actionResult = handled
                                ? "该操作已处理过，无需重复确认"
                                : confirmErrorMessage(message);
                        adapter.update(position, current);
                        if (handled && confirm) {
                            recordExecuted(action, current.actionResult);
                        }
                    }
                });
    }

    /** 从提案参数里取浇水时长；不是 pump_on 则返回 0（不显示倒计时） */
    private int pumpDurationSec(AiAction action) {
        if (action == null || !"pump_on".equals(action.action)) {
            return 0;
        }
        Object raw = null;
        if (action.args instanceof java.util.Map) {
            raw = ((java.util.Map<?, ?>) action.args).get("duration_sec");
        }
        if (raw instanceof Number) {
            return Math.max(1, ((Number) raw).intValue());
        }
        if (raw instanceof String) {
            try {
                return Math.max(1, Integer.parseInt(((String) raw).trim()));
            } catch (NumberFormatException ignored) {
                // 落到默认时长
            }
        }
        return 5;
    }

    /** 把“已确认执行”的结果写入 history，避免模型重复提议同一操作 */
    private void recordExecuted(AiAction action, String result) {
        if (action == null) {
            return;
        }
        String summary = TextUtils.isEmpty(action.summary) ? "操作" : action.summary;
        String tail = TextUtils.isEmpty(result) ? "" : "（" + result + "）";
        addTurn("assistant", "已执行：" + summary + tail);
    }

    private ChatAdapter.Msg messageAt(int position) {
        if (position < 0 || position >= messages.size()) {
            return null;
        }
        return messages.get(position);
    }

    private String confirmErrorMessage(String message) {
        String msg = message == null ? "" : message;
        if (msg.contains("过期") || msg.contains("expired")) {
            return "提案已过期（3 分钟内有效），请重新向 AI 提问。";
        }
        if (msg.contains("401") || msg.contains("登录")) {
            return "登录已过期，请重新登录后再确认。";
        }
        return "提交失败：" + (msg.isEmpty() ? "网络异常，请重试" : msg);
    }

    private void scrollToBottom() {
        rvChat.post(new Runnable() {
            @Override
            public void run() {
                if (adapter.getItemCount() > 0) {
                    rvChat.smoothScrollToPosition(adapter.getItemCount() - 1);
                }
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

}
