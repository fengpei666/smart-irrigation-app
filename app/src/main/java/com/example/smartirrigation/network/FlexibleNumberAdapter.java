package com.example.smartirrigation.network;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * 宽容的数值解析：把 ""、null、"30"、"30.0" 等写法都吃下来，
 * 避免某一列为空字符串就导致整包 JSON 解析失败（页面表现为“网络异常”）。
 * 基本类型返回 0，包装类型返回 null，保持“null = 无数据”的语义。
 */
final class FlexibleNumberAdapter extends TypeAdapter<Number> {

    private enum Kind {INT, LONG, DOUBLE, FLOAT}

    private final Kind kind;
    private final boolean primitive;

    private FlexibleNumberAdapter(Kind kind, boolean primitive) {
        this.kind = kind;
        this.primitive = primitive;
    }

    static TypeAdapter<Number> of(Class<?> type) {
        if (type == int.class) {
            return new FlexibleNumberAdapter(Kind.INT, true);
        }
        if (type == Integer.class) {
            return new FlexibleNumberAdapter(Kind.INT, false);
        }
        if (type == long.class) {
            return new FlexibleNumberAdapter(Kind.LONG, true);
        }
        if (type == Long.class) {
            return new FlexibleNumberAdapter(Kind.LONG, false);
        }
        if (type == double.class) {
            return new FlexibleNumberAdapter(Kind.DOUBLE, true);
        }
        if (type == Double.class) {
            return new FlexibleNumberAdapter(Kind.DOUBLE, false);
        }
        if (type == float.class) {
            return new FlexibleNumberAdapter(Kind.FLOAT, true);
        }
        return new FlexibleNumberAdapter(Kind.FLOAT, false);
    }

    @Override
    public void write(JsonWriter out, Number value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value);
        }
    }

    @Override
    public Number read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return fallback();
        }
        if (token != JsonToken.NUMBER && token != JsonToken.STRING) {
            in.skipValue();
            return fallback();
        }
        String raw = in.nextString().trim();
        if (raw.isEmpty()) {
            return fallback();
        }
        try {
            switch (kind) {
                case INT:
                    return (int) Double.parseDouble(raw);
                case LONG:
                    return (long) Double.parseDouble(raw);
                case FLOAT:
                    return Float.parseFloat(raw);
                default:
                    return Double.parseDouble(raw);
            }
        } catch (NumberFormatException e) {
            return fallback();
        }
    }

    private Number fallback() {
        if (!primitive) {
            return null;
        }
        switch (kind) {
            case INT:
                return 0;
            case LONG:
                return 0L;
            case FLOAT:
                return 0f;
            default:
                return 0d;
        }
    }
}
