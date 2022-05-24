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

package network.balanced.score.core.loans.asset;

import network.balanced.score.core.loans.linkedlist.LinkedListDB;
import network.balanced.score.core.loans.utils.Token;
import score.Address;
import score.BranchDB;
import score.Context;
import score.VarDB;

import java.math.BigInteger;
import java.util.Map;

import static java.util.Map.entry;
import static network.balanced.score.core.loans.utils.LoansConstants.SICX_SYMBOL;

public class Asset {

    private final BranchDB<String, VarDB<BigInteger>> assetAddedTime = Context.newBranchDB("added", BigInteger.class);
    private final BranchDB<String, VarDB<Address>> assetAddress = Context.newBranchDB("address", Address.class);
    private final BranchDB<String, VarDB<BigInteger>> badDebt = Context.newBranchDB("bad_debt", BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> liquidationPool = Context.newBranchDB("liquidation_pool",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalBurnedTokens = Context.newBranchDB("burned",
            BigInteger.class);
    private final BranchDB<String, VarDB<Boolean>> isCollateral = Context.newBranchDB("is_collateral", Boolean.class);
    private final BranchDB<String, VarDB<Boolean>> active = Context.newBranchDB("active", Boolean.class);
    private final BranchDB<String, VarDB<Boolean>> deadMarket = Context.newBranchDB("dead_market", Boolean.class);

    private final String dbKey;

    Asset(String key) {
        dbKey = key;
    }

    public void burn(BigInteger amount) {
        Context.call(assetAddress.at(dbKey).get(), "burn", amount);
        VarDB<BigInteger> totalBurnedTokens = this.totalBurnedTokens.at(dbKey);
        totalBurnedTokens.set(totalBurnedTokens.getOrDefault(BigInteger.ZERO).add(amount));
    }

    public void burnFrom(Address from, BigInteger amount) {
        Context.call(assetAddress.at(dbKey).get(), "burnFrom", from, amount);
        VarDB<BigInteger> totalBurnedTokens = this.totalBurnedTokens.at(dbKey);
        totalBurnedTokens.set(totalBurnedTokens.getOrDefault(BigInteger.ZERO).add(amount));
    }

    public BigInteger getAssetAddedTime() {
        return assetAddedTime.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public Address getAssetAddress() {
        return assetAddress.at(dbKey).get();
    }

    public void setBadDebt(BigInteger badDebt) {
        this.badDebt.at(dbKey).set(badDebt);
    }

    public BigInteger getBadDebt() {
        return badDebt.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public void setLiquidationPool(BigInteger liquidationPool) {
        this.liquidationPool.at(dbKey).set(liquidationPool);
    }

    public BigInteger getLiquidationPool() {
        return liquidationPool.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    private BigInteger getTotalBurnedTokens() {
        return totalBurnedTokens.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public boolean isCollateral() {
        return isCollateral.at(dbKey).getOrDefault(false);
    }

    public void setActive(Boolean active) {
        this.active.at(dbKey).set(active);
    }

    public boolean isActive() {
        return active.at(dbKey).getOrDefault(false);
    }

    boolean isDeadMarket() {
        return deadMarket.at(dbKey).getOrDefault(false);
    }

    /**
     * Calculates whether the market is dead and sets the dead market flag. A dead market is defined as being below
     * the point at which total debt equals the minimum value of collateral that could be backing it.
     */
    public boolean checkForDeadMarket() {
        if (isCollateral() || !isActive()) {
            return false;
        }

        BigInteger badDebt = getBadDebt();

        Address assetAddress = this.assetAddress.at(dbKey).get();
        Token assetContract = new Token(assetAddress);

        BigInteger outStanding = assetContract.totalSupply().subtract(badDebt);

        Address sicxAddress = AssetDB.getAsset(SICX_SYMBOL).getAssetAddress();
        Token sicxContract = new Token(sicxAddress);

        // [Multi-collateral] Here it assumes every token should be denominated in terms of sicx.
        BigInteger poolValue =
                getLiquidationPool().multiply(assetContract.priceInLoop()).divide(sicxContract.priceInLoop());
        BigInteger netBadDebt = badDebt.subtract(poolValue);
        Boolean isDead = netBadDebt.compareTo(outStanding.divide(BigInteger.TWO)) > 0;

        VarDB<Boolean> deadMarket = this.deadMarket.at(dbKey);
        if (deadMarket.getOrDefault(false) != isDead) {
            deadMarket.set(isDead);
        }
        return isDead;
    }

    public LinkedListDB getBorrowers() {
        return new LinkedListDB("borrowers", dbKey);
    }

    public void removeBorrowers(int positionId) {
        getBorrowers().remove(positionId);
    }

    void setAsset(Address assetAddress, BigInteger assetAddedTime, Boolean active, Boolean collateral) {
        this.assetAddress.at(dbKey).set(assetAddress);
        this.assetAddedTime.at(dbKey).set(assetAddedTime);
        this.active.at(dbKey).set(active);
        this.isCollateral.at(dbKey).set(collateral);
    }

    Map<String, Object> toMap() {
        Address assetAddress = this.assetAddress.at(dbKey).get();
        Token tokenContract = new Token(assetAddress);

        return Map.ofEntries(
                entry("symbol", tokenContract.symbol()),
                entry("address", assetAddress),
                entry("peg", tokenContract.getPeg()),
                entry("added", getAssetAddedTime()),
                entry("is_collateral", isCollateral()),
                entry("active", isActive()),
                entry("borrowers", getBorrowers().size()),
                entry("total_supply", tokenContract.totalSupply()),
                entry("total_burned", getTotalBurnedTokens()),
                entry("bad_debt", getBadDebt()),
                entry("liquidation_pool", getLiquidationPool()),
                entry("dead_market", isDeadMarket())
        );
    }
}