/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.quorum.gauge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.quorum.gauge.common.QuorumNetworkProperty;
import com.quorum.gauge.common.QuorumNode;
import com.quorum.gauge.core.AbstractSpecImplementation;
import com.quorum.gauge.services.IstanbulService;
import com.quorum.gauge.services.QLightService;
import com.thoughtworks.gauge.ContinueOnFailure;
import com.thoughtworks.gauge.Gauge;
import com.thoughtworks.gauge.Step;
import com.thoughtworks.gauge.datastore.DataStoreFactory;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Service
public class Istanbul extends AbstractSpecImplementation {
    private static final Logger logger = LoggerFactory.getLogger(Istanbul.class);

    @Autowired
    private IstanbulService istanbulService;

    @Autowired
    private QLightService qLightService;

    @Autowired
    private Environment environment;


    @Step({"The consensus should work at the beginning", "The consensus should work after resuming", "The consensus should work after stopping F validators"})
    public void verifyConsensus() {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            Gauge.writeMessage(ow.writeValueAsString(networkProperty));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Arrays.stream(environment.getActiveProfiles()).forEach(Gauge::writeMessage);

        int diff = 3;
        // wait for blockheight increases by 3 from the current one
        waitForBlockHeight(currentBlockNumber().intValue(), currentBlockNumber().intValue() + diff);
    }

    @Step("Among all validators, stop F validators")
    public void stopFValidators() {
        stopValidators(false);
    }

    @Step("Among all validators, stop F+1 validators")
    public void stopMoreThanFValidators() {
        stopValidators(true);
    }

    @ContinueOnFailure
    @Step("The consensus should stop")
    public void verifyConsensusStopped() {
        BigInteger lastBlockNumber = utilService.getCurrentBlockNumber().blockingFirst().getBlockNumber();
        // wait for 10 seconds and get the block number
        BigInteger newBlockNumber = Observable.timer(10, TimeUnit.SECONDS).flatMap(x -> utilService.getCurrentBlockNumber()).blockingFirst().getBlockNumber();

        assertThat(newBlockNumber).isEqualTo(lastBlockNumber);
    }

    @Step("Resume the stopped validators")
    public void startValidators() {
        List<QuorumNode> nodes = mustHaveValue(DataStoreFactory.getScenarioDataStore(), "stoppedNodes", List.class);
        Observable.fromIterable(nodes)
                .flatMap(istanbulService::startMining)
                .blockingSubscribe();
    }

    private void stopValidators(boolean stopMoreThanF) {
        int totalNodesConfigured = numberOfQuorumNodes();
        List<QuorumNode> configuredNodes = Observable.fromArray(QuorumNode.values()).take(totalNodesConfigured).toList().blockingGet();

        // in qlight networks we cannot use a qlight client for network API requests as their only peer is the corresponding server
        // so, make sure to get a full node for these requests
        QuorumNode fullNode = configuredNodes.stream().filter(n -> !qLightService.isQLightClient(n)).findFirst().get();

        Gauge.writeMessage("CHRISSY using %s as full node for RPC queries", fullNode.name());

        int totalNodesLive = utilService.getNumberOfNodes(fullNode) + 1;
        List<String> validatorAddresses = istanbulService.getValidators(fullNode).blockingFirst().get();

        int numOfValidatorsToStop = Math.round((validatorAddresses.size() - 1) / 3.0f);
        if(stopMoreThanF) {
            numOfValidatorsToStop++;
        }

        // we only can stop validators that are configured
        assertThat(numOfValidatorsToStop).describedAs("Not enough configured validators to perform STOP operation").isLessThanOrEqualTo(totalNodesConfigured);
        Gauge.writeMessage(String.format("Stopping %d validators from total of %d validators", numOfValidatorsToStop, totalNodesLive));

        List<QuorumNode> validatorNodes = Observable.fromArray(QuorumNode.values())
            .take(totalNodesConfigured)
            .filter(n -> {
                QuorumNetworkProperty.Node nn = networkProperty.getNode(n.name());
                return validatorAddresses.contains(nn.getIstanbulValidatorId());
            })
            .toList()
            .blockingGet();

        Collections.shuffle(validatorNodes);
        List<QuorumNode> stoppedNodes = validatorNodes.subList(0, numOfValidatorsToStop);
        Observable.fromIterable(stoppedNodes)
            .flatMap(istanbulService::stopMining)
            .blockingSubscribe();

        DataStoreFactory.getScenarioDataStore().put("stoppedNodes", stoppedNodes);
    }
}
