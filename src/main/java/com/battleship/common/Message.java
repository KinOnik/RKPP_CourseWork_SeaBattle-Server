package com.battleship.common;

import java.io.Serializable;

public class Message implements Serializable {
    private final MessageType type;
    private final Object payload;

    public Message(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public Message(MessageType type) {
        this(type, null);
    }

    public MessageType getType() { return type; }
    public Object getPayload() { return payload; }

}