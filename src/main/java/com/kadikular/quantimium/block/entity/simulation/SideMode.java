package com.kadikular.quantimium.block.entity.simulation;

public enum SideMode {
    DISABLED(false, false),
    INPUT(true, false),
    OUTPUT(false, true),
    BOTH(true, true);

    private final boolean input;
    private final boolean output;

    SideMode(boolean input, boolean output) {
        this.input = input;
        this.output = output;
    }

    public boolean allowsInput() { return input; }
    public boolean allowsOutput() { return output; }

    public SideMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static SideMode byId(int id) {
        return values()[Math.floorMod(id, values().length)];
    }
}
