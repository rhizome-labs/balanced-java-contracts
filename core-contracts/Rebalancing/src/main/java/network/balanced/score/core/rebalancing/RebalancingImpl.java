/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.rebalancing;

import network.balanced.score.lib.interfaces.Rebalancing;
import network.balanced.score.lib.utils.Names;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.rebalancing.Constants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Constants.EXA;

public class RebalancingImpl implements Rebalancing {

    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    public static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private final VarDB<BigInteger> priceThreshold = Context.newVarDB(PRICE_THRESHOLD, BigInteger.class);

    public RebalancingImpl(Address _governance) {
        if (governance.getOrDefault(null) == null) {
            Context.require(_governance.isContract(), TAG + ": Governance address should be a contract");
            governance.set(_governance);
            // priceThreshold.set(BigInteger.valueOf(100000000000000000L));
        }

        setGovernance(governance.get());
    }

    @External(readonly = true)
    public String name() {
        return Names.REBALANCING;
    }

    @External
    public void setAdmin(Address _address) {
        only(governance);
        admin.set(_address);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void updateAddress(String name) {
        resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return getAddressByName(name);
    }

    private BigInteger calculateTokensToSell(BigInteger price, BigInteger fromTokenLiquidity,
                                             BigInteger toTokenLiquidity) {
        return price.multiply(fromTokenLiquidity).multiply(toTokenLiquidity).divide(EXA).sqrt().subtract(fromTokenLiquidity);
    }

    @External
    public void setPriceDiffThreshold(BigInteger _value) {
        only(getGovernance());
        priceThreshold.set(_value);
    }

    @External(readonly = true)
    public BigInteger getPriceChangeThreshold() {
        return priceThreshold.get();
    }

    /**
     * Checks the Rebalancing status of the pool i.e. whether the difference between oracle price and dex pool price
     * are more than threshold or not. If it is more than the threshold then the function returns a list. If the
     * first element of the list is True then it's forward rebalancing and if the last element of the list is True,
     * it's the reverse rebalancing. The second element of the list specifies the amount of tokens required to
     * balance the pool.
     *
     * @return {List<Object> }   [<Positive difference>, <Tokens to sell>, <Negative difference>]
     */

    @External(readonly = true)
    @SuppressWarnings("unchecked")
    public List<Object> getRebalancingStatusFor(Address collateralAddress) {

        List<Object> results = new ArrayList<>(3);

        Address bnusdScore = getBnusd();
        Address dexScore = getDex();
        Address sicxScore = getSicx();
        Address oracleScore = getBalancedOracle();
        BigInteger threshold = priceThreshold.get();

        Context.require(bnusdScore != null && dexScore != null && sicxScore != null && threshold != null);
        String symbol = Context.call(String.class, collateralAddress, "symbol");
        BigInteger poolID = Context.call(BigInteger.class, dexScore, "getPoolId", collateralAddress, bnusdScore);

        BigInteger bnusdPriceInIcx = (BigInteger) Context.call(oracleScore, "getPriceInLoop", "USD");
        BigInteger assetPriceInIcx = (BigInteger) Context.call(oracleScore, "getPriceInLoop", symbol);
        BigInteger actualBnusdPriceInSicx = bnusdPriceInIcx.multiply(EXA).divide(assetPriceInIcx);

        Map<String, Object> poolStats = (Map<String, Object>) Context.call(dexScore, "getPoolStats", poolID);
        BigInteger assetLiquidity = (BigInteger) poolStats.get("base");
        BigInteger bnusdLiquidity = (BigInteger) poolStats.get("quote");
        BigInteger bnusdPriceInSicx = assetLiquidity.multiply(EXA).divide(bnusdLiquidity);

        BigInteger priceDifferencePercentage =
                actualBnusdPriceInSicx.subtract(bnusdPriceInSicx).multiply(EXA).divide(actualBnusdPriceInSicx);

        // We can get three conditions with price difference.
        // a. priceDifference > threshold (dex price of bnusd is low),
        // b. priceDifference < -threshold, (dex price of bnusd is high)
        // c. priceDifference within [-threshold, threshold] (dex price is within range)

        // If bnUSD price is less in dex, to increase we would need to add sicx in the pool and get back bnUSD
        // Buy bnUSD from the pool --> Sell sicx.

        // If bnUSD price is more in dex, to reduce we would need to add bnusd in the pool, and get back sicx
        // Sell bnUSD to the pool --> buy sicx.
        BigInteger tokensToSell;
        boolean forward = priceDifferencePercentage.compareTo(threshold) > 0;
        assert threshold != null;
        boolean reverse = priceDifferencePercentage.compareTo(threshold.negate()) < 0;
        if (forward) {
            //Add sicx in the pool i.e. buy bnusd from the pool and sell icx. pair: sicx/bnusd
            tokensToSell = calculateTokensToSell(actualBnusdPriceInSicx, assetLiquidity, bnusdLiquidity);
        } else if (reverse) {
            // Add bnusd in the pool i.e. buy sicx from the pool and sell bnusd. pair bnusd/sicx
            BigInteger actualAssetPriceInBnusd = assetPriceInIcx.multiply(EXA).divide(bnusdPriceInIcx);
            tokensToSell = calculateTokensToSell(actualAssetPriceInBnusd, bnusdLiquidity, assetLiquidity);
        } else {
            tokensToSell = BigInteger.ZERO;
        }

        results.add(forward);
        results.add(tokensToSell);
        results.add(reverse);
        return results;
    }

    @External
    public void rebalance(@Optional Address collateralAddress) {
        optionalDefault(collateralAddress, getSicx());
        Address loansScore = getLoans();
        String symbol = Context.call(String.class, collateralAddress, "symbol");
        Context.require(loansScore != null);
        List<Object> status = getRebalancingStatusFor(collateralAddress);
        boolean forward = (boolean) status.get(0);
        BigInteger tokenAmount = (BigInteger) status.get(1);
        boolean reverse = (boolean) status.get(2);
        if (forward && tokenAmount.signum() > 0) {
            Context.call(loansScore, "raisePrice", symbol, tokenAmount);
        } else if (reverse && tokenAmount.signum() > 0) {
            Context.call(loansScore, "lowerPrice", symbol, tokenAmount.abs());
        }
    }
}