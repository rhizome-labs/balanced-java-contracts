package network.balanced.score.core.governance.utils;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import network.balanced.score.lib.structs.DistributionPercentage;

import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.governance.GovernanceImpl.*;
import static network.balanced.score.core.governance.utils.GovernanceConstants.*;
import static network.balanced.score.lib.utils.Math.pow;

public class SetupManager {
    
    public static void configureBalanced() {
        for (Map<String, Object> asset : ASSETS) {
            Address tokenAddress = AddressManager.get((String) asset.get("address"));
            call(
                    AddressManager.get("loans"),
                    "addAsset",
                    tokenAddress,
                    asset.get("active"),
                    asset.get("collateral")
            );
            call(AddressManager.get("dividends"), "addAcceptedTokens", tokenAddress);
        }

        Address[] acceptedFeeTokens = new Address[]{
            AddressManager.get("sicx"), 
            AddressManager.get("bnUSD"), 
            AddressManager.get("baln")
        };

        call(AddressManager.get("feehandler"), "setAcceptedDividendTokens", (Object) acceptedFeeTokens);
    }

    public static void launchBalanced() {
        if (launched.get()) {
            return;
        }

        launched.set(true);

        BigInteger day = _getDay();
        launchDay.set(day);
        BigInteger timeDelta = BigInteger.valueOf(Context.getBlockTimestamp()).add(timeOffset.getOrDefault(BigInteger.ZERO));

        launchTime.set(timeDelta);
        _setTimeOffset(timeDelta);

        for (Map<String, String> source : DATA_SOURCES) {
            call(AddressManager.get("rewards"), "addNewDataSource", source.get("name"), AddressManager.get(source.get("address")));
        }

        call(AddressManager.get("rewards"), "updateBalTokenDistPercentage", (Object) RECIPIENTS);
    }

    public static void createBnusdMarket() {
        BigInteger value = Context.getValue();
        Context.require(!value.equals(BigInteger.ZERO), TAG + "ICX sent must be greater than zero.");

        Address dexAddress = AddressManager.get("dex");
        Address sICXAddress = AddressManager.get("sicx");
        Address bnUSDAddress = AddressManager.get("bnUSD");
        Address stakedLpAddress = AddressManager.get("stakedLp");
        Address stakingAddress = AddressManager.get("staking");
        Address rewardsAddress = AddressManager.get("rewards");
        Address loansAddress = AddressManager.get("loans");

        BigInteger price = call(BigInteger.class, bnUSDAddress, "priceInLoop");
        BigInteger amount = EXA.multiply(value).divide(price.multiply(BigInteger.valueOf(7)));
        call(value.divide(BigInteger.valueOf(7)), stakingAddress, "stakeICX", Context.getAddress(),
                new byte[0]);
        call(Context.getBalance(Context.getAddress()), loansAddress, "depositAndBorrow", "bnUSD", amount, Context.getAddress(), BigInteger.ZERO);

        BigInteger bnUSDValue = call(BigInteger.class, bnUSDAddress, "balanceOf", Context.getAddress());
        BigInteger sICXValue = call(BigInteger.class, sICXAddress, "balanceOf", Context.getAddress());

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        call(bnUSDAddress, "transfer", dexAddress, bnUSDValue, depositData.toString().getBytes());
        call(sICXAddress, "transfer", dexAddress, sICXValue, depositData.toString().getBytes());

        call(dexAddress, "add", sICXAddress, bnUSDAddress, sICXValue, bnUSDValue, false);
        String name = "sICX/bnUSD";
        BigInteger pid = call(BigInteger.class, dexAddress, "getPoolId", sICXAddress, bnUSDAddress);
        call(dexAddress, "setMarketName", pid, name);

        call(rewardsAddress, "addNewDataSource", name, dexAddress);
        call(stakedLpAddress, "addPool", pid);
        DistributionPercentage[] recipients = new DistributionPercentage[]{
            createDistributionPercentage("Loans", BigInteger.valueOf(25).multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("DAOfund", BigInteger.valueOf(225).multiply(pow(BigInteger.TEN, 15))),
            createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15)))
        };

        call(AddressManager.get("rewards"), "updateBalTokenDistPercentage", (Object) recipients);
    }

    public static void createBalnMarket(BigInteger _bnUSD_amount, BigInteger _baln_amount) {
        Address dexAddress = AddressManager.get("dex");
        Address balnAddress = AddressManager.get("baln");
        Address bnUSDAddress = AddressManager.get("bnUSD");
        Address stakedLpAddress = AddressManager.get("stakedLp");
        Address rewardsAddress = AddressManager.get("rewards");
        Address loansAddress = AddressManager.get("loans");

        call(rewardsAddress, "claimRewards");
        call(loansAddress, "depositAndBorrow", "bnUSD", _bnUSD_amount, Context.getAddress(), BigInteger.ZERO);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        call(bnUSDAddress, "transfer", dexAddress, _bnUSD_amount, depositData.toString().getBytes());
        call(balnAddress, "transfer", dexAddress, _baln_amount, depositData.toString().getBytes());

        call(dexAddress, "add", balnAddress, bnUSDAddress, _baln_amount, _bnUSD_amount, false);
        String name = "BALN/bnUSD";
        BigInteger pid = call(BigInteger.class, dexAddress, "getPoolId", balnAddress, bnUSDAddress);
        call(dexAddress, "setMarketName", pid, name);

        call(rewardsAddress, "addNewDataSource", name, dexAddress);
        call(stakedLpAddress, "addPool", pid);

        DistributionPercentage[] recipients = new DistributionPercentage[]{
                createDistributionPercentage("Loans", BigInteger.valueOf(25).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("DAOfund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15))),
                createDistributionPercentage("BALN/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15)))
        };

        call(rewardsAddress, "updateBalTokenDistPercentage", (Object) recipients);
    }

    public static void createBalnSicxMarket(BigInteger _sicx_amount, BigInteger _baln_amount) {
        Address dexAddress = AddressManager.get("dex");
        Address balnAddress = AddressManager.get("baln");
        Address sICXAddress = AddressManager.get("sicx");
        Address stakedLpAddress = AddressManager.get("stakedLp");
        Address rewardsAddress = AddressManager.get("rewards");

        call(rewardsAddress, "claimRewards");

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        call(sICXAddress, "transfer", dexAddress, _sicx_amount, depositData.toString().getBytes());
        call(balnAddress, "transfer", dexAddress, _baln_amount, depositData.toString().getBytes());

        call(dexAddress, "add", balnAddress, sICXAddress, _baln_amount, _sicx_amount, false);
        String name = "BALN/sICX";
        BigInteger pid = call(BigInteger.class, dexAddress, "getPoolId", balnAddress, sICXAddress);
        call(dexAddress, "setMarketName", pid, name);

        call(rewardsAddress, "addNewDataSource", name, dexAddress);
        call(stakedLpAddress, "addPool", pid);

        DistributionPercentage[] recipients = new DistributionPercentage[]{
                createDistributionPercentage("Loans", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("DAOfund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(15).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("BALN/bnUSD", BigInteger.valueOf(15).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("BALN/sICX", BigInteger.valueOf(10).multiply(pow(BigInteger.TEN, 16)))
        };

        call(AddressManager.get("rewards"), "updateBalTokenDistPercentage", (Object) recipients);
    }
}
