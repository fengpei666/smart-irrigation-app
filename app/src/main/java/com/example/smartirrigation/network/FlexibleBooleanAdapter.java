package com.example.smartirrigation.network;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * 宽容的布尔解析：PHP/MySQL 习惯返回 1/0、"1"/"0"、"true"/"false"，
 * 而 Gson 默认只认 true/false 字面量，会让整包解析直接失败（表现为“网络异常”）。
 * 这里统一兼容，避免某张表用 tinyint 就整页接口不可用。
 */
class FlexibleBooleanAdapter extends TypeAdapter<Boolean> {

    @Override
    public void write(JsonWriter out, Boolean value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value);
        }
    }

    @Override
    public Boolean read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        switch (token) {
            case NULL:
                in.nextNull();
                return Boolean.FALSE;
            case BOOLEAN:
                return in.nextBoolean();
            case NUMBER:
                return in.nextInt() != 0;
            case STRING:
                String text = in.nextString().trim();
                return "1".equals(text)
                        || "true".equalsIgnoreCase(text)
                        || "yes".equalsIgnoreCase(text)
                        || "on".equalsIgnoreCase(text);
            default:
                in.skipValue();
                return Boolean.FALSE;
        }
    }
}
