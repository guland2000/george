package io.pijun.george.api;

import java.util.Arrays;

import io.pijun.george.Hex;

public class Message {

    public long id;
    public byte[] senderId;
    public byte[] cipherText;
    public byte[] nonce;

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", senderId=" + Hex.toHexString(senderId) +
                ", cipherText=" + Hex.toHexString(cipherText) +
                ", nonce=" + Hex.toHexString(nonce) +
                '}';
    }
}
