package com.quorum.gauge;

import com.quorum.gauge.common.QuorumNode;
import com.quorum.gauge.core.AbstractSpecImplementation;
import com.thoughtworks.gauge.Step;
import com.thoughtworks.gauge.datastore.DataStoreFactory;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Service
public class PublicSmartContract extends AbstractSpecImplementation {

    @Step("Deploy `ClientReceipt` smart contract from a default account in <node>, named this contract as <contractName>.")
    public void deployClientReceiptSmartContract(QuorumNode node, String contractName) {
        Contract c = contractService.createClientReceiptSmartContract(node).toBlocking().first();

        DataStoreFactory.getScenarioDataStore().put(contractName, c);
    }

    @Step("<contractName> is mined.")
    public void verifyContractIsMined(String contractName) {
        Contract c = (Contract) DataStoreFactory.getScenarioDataStore().get(contractName);

        assertThat(c.getTransactionReceipt().isPresent()).isTrue();
        assertThat(c.getTransactionReceipt().get().getBlockNumber()).isNotEqualTo(currentBlockNumber());
    }

    @Step("Execute <contractName>'s `deposit()` function <count> times with arbitrary id and value from <node>.")
    public void excuteDesposit(String contractName, int count, QuorumNode node) {
        Contract c = (Contract) DataStoreFactory.getScenarioDataStore().get(contractName);
        List<Observable<TransactionReceipt>> observables = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            observables.add(contractService.updateClientReceipt(node, c.getContractAddress(), BigInteger.TEN).subscribeOn(Schedulers.io()));
        }
        List<TransactionReceipt> receipts = Observable.zip(observables, objects -> Observable.from(objects).map(o -> (TransactionReceipt)o ).toList().toBlocking().first()).toBlocking().first();

        DataStoreFactory.getScenarioDataStore().put("receipts", receipts);
    }

    @Step("<node> has received <expectedTxCount> transactions which totally contain <expectedEventCount> log events.")
    public void verifyLogEvents(QuorumNode node, int expectedTxCount, int expectedEventCount) {
        List<TransactionReceipt> receipts = (List<TransactionReceipt>) DataStoreFactory.getScenarioDataStore().get("receipts");
        int actualTxCount = 0;
        int actualEventCount = 0;
        for (TransactionReceipt r : receipts) {
            assertThat(r.isStatusOK()).isTrue();
            assertThat(r.getBlockNumber()).isNotEqualTo(BigInteger.ZERO);
            actualTxCount++;
            actualEventCount += r.getLogs().size();
        }

        assertThat(actualTxCount).as("Transaction Count").isEqualTo(expectedTxCount);
        assertThat(actualEventCount).as("Log Event Count").isEqualTo(expectedEventCount);
    }

}
