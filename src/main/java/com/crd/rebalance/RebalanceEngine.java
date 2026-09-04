package com.crd.rebalance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a target allocation into the orders needed to reach it.
 *
 * <p>The rule, restated from the assessment: {@code variance = current% - target%} in percentage
 * points; a negative variance is a shortfall to buy and a positive variance is an excess to sell.
 * The cash value of the gap is {@code -variance / 100 * totalAssets}, and the share count is that
 * value divided by the unit price, reduced to whole shares by the configured
 * {@link RoundingPolicy}.
 *
 * <p>Exact zero variance is generally unreachable: closing the assessment's IBM gap exactly would
 * take 66.6667 shares. The achievable goal is residual variance inside a tolerance band, and the
 * result object reports that residual so a caller can assert against it.
 *
 * <p>Instances hold no state and never mutate their arguments, so a single engine is safe to
 * share across threads and across accounts.
 */
public final class RebalanceEngine {

    /** Rebalances using {@link RebalanceConfig#defaults()}. */
    public RebalanceResult rebalance(Account account) {
        return rebalance(account, RebalanceConfig.defaults());
    }

    public RebalanceResult rebalance(Account account, RebalanceConfig config) {
        if (account == null) {
            throw new IllegalArgumentException("account must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        List<Order> buys = new ArrayList<>();
        List<Order> sells = new ArrayList<>();
        Map<String, BigDecimal> postTradeValues = new LinkedHashMap<>();
        Map<String, BigDecimal> postTradeVariances = new LinkedHashMap<>();

        for (Security security : account.securities()) {
            BigDecimal effectiveTargetPct = effectiveTargetPct(security, account);
            BigDecimal variancePct = security.currentPct().subtract(effectiveTargetPct);
            long quantity = quantityFor(security, account, config, variancePct);

            if (quantity > 0) {
                buys.add(new Order(security.symbol(), Side.BUY,
                        quantity, security.unitPrice().multiply(BigDecimal.valueOf(quantity))));
            } else if (quantity < 0) {
                sells.add(new Order(security.symbol(), Side.SELL,
                        -quantity, security.unitPrice().multiply(BigDecimal.valueOf(-quantity))));
            }

            BigDecimal postValue = valueOf(security.currentPct(), account.totalAssets())
                    .add(security.unitPrice().multiply(BigDecimal.valueOf(quantity)));
            postTradeValues.put(security.symbol(), postValue);
            postTradeVariances.put(security.symbol(),
                    asPercentOf(postValue, account.totalAssets()).subtract(effectiveTargetPct));
        }

        List<Order> sequenced = new ArrayList<>();
        if (config.sellsFirst()) {
            sequenced.addAll(sells);
            sequenced.addAll(buys);
        } else {
            sequenced.addAll(buys);
            sequenced.addAll(sells);
        }

        BigDecimal running = account.cash();
        BigDecimal minimum = running;
        for (Order order : sequenced) {
            running = running.add(order.cashEffect());
            minimum = minimum.min(running);
        }

        return new RebalanceResult(sequenced, postTradeValues, postTradeVariances,
                account.cash(), running, minimum);
    }

    /**
     * Signed whole-share quantity: positive to buy, negative to sell, zero to leave alone.
     * Suppression rules are applied in the order tolerance band, then rounding, then round lot,
     * then minimum notional, so a line can be dropped for any of four distinct reasons.
     */
    private long quantityFor(Security security, Account account, RebalanceConfig config, BigDecimal variancePct) {
        if (variancePct.abs().compareTo(config.toleranceBandPct()) <= 0) {
            return 0L;
        }

        BigDecimal tradeNotional = valueOf(variancePct.negate(), account.totalAssets());

        // Divide exactly and round once, at the share-quantity step. Rounding any intermediate
        // value would let error compound into the order.
        long quantity = tradeNotional
                .divide(security.unitPrice(), 0, config.roundingPolicy().mode())
                .longValueExact();

        quantity = (quantity / config.lotSize()) * config.lotSize();
        if (quantity == 0L) {
            return 0L;
        }

        BigDecimal notional = security.unitPrice().multiply(BigDecimal.valueOf(Math.abs(quantity)));
        return notional.compareTo(config.minTradeNotional()) < 0 ? 0L : quantity;
    }

    /** Unvested assets cannot be traded, so targets apply to the vested base only (ASM-05). */
    private static BigDecimal effectiveTargetPct(Security security, Account account) {
        return security.targetPct().multiply(account.vestedPct()).divide(Rebalancing.HUNDRED, Rebalancing.MC);
    }

    private static BigDecimal valueOf(BigDecimal percent, BigDecimal totalAssets) {
        return percent.multiply(totalAssets).divide(Rebalancing.HUNDRED, Rebalancing.MC);
    }

    private static BigDecimal asPercentOf(BigDecimal value, BigDecimal totalAssets) {
        if (totalAssets.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(Rebalancing.HUNDRED)
                .divide(totalAssets, Rebalancing.PCT_SCALE, Rebalancing.MC.getRoundingMode());
    }
}
