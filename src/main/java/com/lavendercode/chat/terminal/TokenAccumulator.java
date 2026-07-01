package com.lavendercode.chat.terminal;

public class TokenAccumulator {
    private int totalInput = 0;
    private int totalOutput = 0;

    public void add(int in, int out) { totalInput += in; totalOutput += out; }
    public int getTotalInput() { return totalInput; }
    public int getTotalOutput() { return totalOutput; }
    public int getTotal() { return totalInput + totalOutput; }
    public void reset() { totalInput = 0; totalOutput = 0; }
}
