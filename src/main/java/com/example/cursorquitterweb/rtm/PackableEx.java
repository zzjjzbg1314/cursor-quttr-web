package com.example.cursorquitterweb.rtm;

public interface PackableEx extends Packable {
    void unmarshal(ByteBuf in);
}
