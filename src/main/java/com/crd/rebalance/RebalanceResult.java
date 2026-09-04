package com.crd.rebalance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outcome of a rebalance: the orders to place, and enough post-trade state to prove they were
 * the right ones.
 *
 * @param orders               orders in the sequence they should be executed
 * @param postTradeValues      market value of each holding once the orders fill
 * @param postTradeVariancePct residual variance per symbol, in percentage points
 * @param openingCash          cash before any order
 * @param endingCash           cash after every order fills
 * @param minimumRunningCash   lowest cash balance reached while executing {@code orders} in
 *                             sequence; negative means the block cannot be funded as ordered
 */
public record RebalanceResult(List<Order> orders, Map<String, BigDecimal> postTradeValues,
                              Map<String, BigDecimal> postTradeVariancePct, BigDecimal openingCash,
                              BigDecimal endingCash, BigDecimal minimumRunningCash) {

    public RebalanceResult {
        orders = List.copyOf(orders);
        postTradeValues = Map.copyOf(postTradeValues);
        postTradeVariancePct = Map.copyOf(postTradeVariancePct);
    }

    public Optional<Order> orderFor(String symbol) {
        return orders.stream().filter(o -> o.symbol().equals(symbol)).findFirst();
    }

    /** Positive for a buy, negative for a sell, zero when no order was generated. */
    public long signedQuantity(String symbol) {
        return orderFor(symbol)
                .map(o -> o.side() == Side.SELL ? -o.quantity() : o.quantity())
                .orElse(0L);
    }

    /** Net cash raised (positive) or consumed (negative) by the whole block. */
    public BigDecimal cashImpact() {
        return endingCash.subtract(openingCash);
    }

    public BigDecimal totalBuyNotional() {
        return notionalFor(Side.BUY);
    }

    public BigDecimal totalSellNotional() {
        return notionalFor(Side.SELL);
    }

    /**
     * Whether the block can be executed without the cash balance ever going negative. This is a
     * property of the order <em>sequence</em>, not just the net total: a self-funding block still
     * overdraws if the buys are sent before the sells that pay for them.
     */
    public boolean isFundable() {
        return minimumRunningCash.signum() >= 0;
    }

    /** Largest residual variance in percentage points, the headline post-trade quality measure. */
    public BigDecimal maxAbsoluteVariancePct() {
        return postTradeVariancePct.values().stream()
                .map(BigDecimal::abs)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal notionalFor(Side side) {
        return orders.stream()
                .filter(o -> o.side() == side)
                .map(Order::notional)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
