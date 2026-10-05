package com.kadikular.quantimium.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** A block whose item carries a tooltip; {@code Block#appendHoverText} no longer exists to hold it. */
public interface BlockTooltip {
    void appendTooltip(Consumer<Component> tooltip);
}
