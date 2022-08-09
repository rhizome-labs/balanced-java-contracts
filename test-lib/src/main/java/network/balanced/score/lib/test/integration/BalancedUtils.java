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

package network.balanced.score.lib.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import network.balanced.score.lib.interfaces.Governance;

public class BalancedUtils {

    public static void executeVote(Balanced balanced, BalancedClient voter, String name, JsonArray transactions) {
        BigInteger day = voter.governance.getDay();
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);
        
        voter.governance.defineVote(name, "test", voteStart, snapshot, transactions.toString());
        BigInteger id = voter.governance.getVoteIndex(name);
        balanced.increaseDay(2);

        for (BalancedClient client : balanced.balancedClients.values()) {
            try {
                client.governance.castVote(id, true);
            } catch (Exception e) {}
        }

        balanced.increaseDay(2);

        voter.governance.evaluateVote(id);
        assertEquals("Executed", voter.governance.checkVote(id).get("status"));
    }

    public static void whitelistToken(Balanced balanced, Address address, BigInteger limit) {
        JsonArray whitelistTokensParams = new JsonArray()
            .add(createParameter(address))
            .add(createParameter(limit));

        JsonArray whitelistTokens = createSingleTransaction(
            balanced.stability._address(), 
            "whitelistTokens", 
            whitelistTokensParams
        );

        balanced.ownerClient.governance.execute(whitelistTokens.toString());
    }

    public static BigInteger hexObjectToBigInteger(Object hexNumber) {
        String hexString = (String) hexNumber;
        if (hexString.startsWith("0x")) {
            return new BigInteger(hexString.substring(2), 16);
        }
        return new BigInteger(hexString, 16);

    }

    public static byte[] getContractBytes(String path) {
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Address createIRC2Token(BalancedClient owner, String name, String symbol) {
        String path = System.getProperty("user.dir") + "/../../test-lib/util-contracts/IRC2Token.jar";
        DefaultScoreClient assetClient = _deploy(Env.getDefaultChain().getEndpointURL(), Env.getDefaultChain().networkId, owner.wallet, path,  Map.of("name", name, "symbol", symbol));
        return assetClient._address();
    }

    public static JsonObject createJsonDistribtion(String name, BigInteger dist) {
        return new JsonObject()
            .add("recipient_name", name)
            .add("dist_percent", dist.toString());
    }
    
    public static JsonObject createJsonDisbusment(String token, BigInteger amount) {
        return new JsonObject()
            .add("address", token)
            .add("amount", amount.intValue());
    }

    public static JsonObject createParameter(String value) {
        return new JsonObject()
            .add("type", "String")
            .add("value", value);
    }

    public static JsonObject createParameter(byte[] value) {
        return new JsonObject()
            .add("type", "bytes")
            .add("value", getHex(value));
    }
    

    public static JsonObject createParameter(Address value) {
        return new JsonObject()
            .add("type", "Address")
            .add("value", value.toString());
    }

    public static JsonObject createParameter(score.Address value) {
        return new JsonObject()
            .add("type", "Address")
            .add("value", value.toString());
    }

    public static JsonObject createParameter(BigInteger value) {
        return new JsonObject()
            .add("type", "int")
            .add("value", value.toString());
    }

    public static JsonObject createParameter(Boolean value) {
        return new JsonObject()
            .add("type", "boolean")
            .add("value", value);
    }

    public static JsonObject createParameter(String type, JsonObject value) {
        return new JsonObject()
            .add("type", type)
            .add("value", value);
    }

    public static JsonObject createParameter(String type, JsonArray value) {
        return new JsonObject()
            .add("type", type)
            .add("value", value);
    }

    public static JsonObject createTransaction(Address address, String method, JsonArray parameters) {
        return new JsonObject()
            .add("address", address.toString())
            .add("method", method)
            .add("parameters", parameters);
    }

    public static JsonArray createSingleTransaction(Address address, String method, JsonArray parameters) {
        return new JsonArray()
            .add(createTransaction(address, method, parameters));
    }

    private static String getHex(byte[] raw) {
        String HEXES = "0123456789ABCDEF";
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }
}
