package com.kadikular.quantimium.laser;

/** A Logistics Controller: everything its light reaches is one logistics network, which it pays to run. */
public interface LogisticsController {

    /** Whether it has the power to move a stack. */
    boolean powered();

    /** Takes {@code fe} to move a stack; whether it had it. */
    boolean pay(long fe);

    /** What the network found, once a second: its members, and whether another controller's light joins its own. */
    void report(java.util.List<LogisticsMember> members, boolean clash);

    /** Its network's members, as last found; none while another controller's light stops it. */
    java.util.List<LogisticsMember> members();
}
