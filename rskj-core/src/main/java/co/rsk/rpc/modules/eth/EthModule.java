/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.rpc.modules.eth;

import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.core.bc.BlockResult;
import co.rsk.db.RepositoryLocator;
import co.rsk.peg.BridgeState;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.trie.TrieStoreImpl;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.converters.CallArgumentsToByteArray;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.copyOfRange;
import static org.ethereum.rpc.TypeConverter.stringHexToBigInteger;
import static org.ethereum.rpc.TypeConverter.toUnformattedJsonHex;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

// TODO add all RPC methods
public class EthModule
    implements EthModuleWallet, EthModuleTransaction {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private static final CallTransaction.Function ERROR_ABI_FUNCTION = CallTransaction.Function.fromSignature("Error", "string");
    private static final byte[] ERROR_ABI_FUNCTION_SIGNATURE = ERROR_ABI_FUNCTION.encodeSignature(); //08c379a0

    private final Blockchain blockchain;
    private final TransactionPool transactionPool;
    private final ReversibleTransactionExecutor reversibleTransactionExecutor;
    private final ExecutionBlockRetriever executionBlockRetriever;
    private final RepositoryLocator repositoryLocator;
    private final EthModuleWallet ethModuleWallet;
    private final EthModuleTransaction ethModuleTransaction;
    private final BridgeConstants bridgeConstants;
    private final BridgeSupportFactory bridgeSupportFactory;
    private final byte chainId;
    private final long gasEstimationCap;

    private ProgramResult estimationResult; // todo(fedejinich) this should be extracted to a Test class

    public EthModule(
            BridgeConstants bridgeConstants,
            byte chainId,
            Blockchain blockchain,
            TransactionPool transactionPool,
            ReversibleTransactionExecutor reversibleTransactionExecutor,
            ExecutionBlockRetriever executionBlockRetriever,
            RepositoryLocator repositoryLocator,
            EthModuleWallet ethModuleWallet,
            EthModuleTransaction ethModuleTransaction,
            BridgeSupportFactory bridgeSupportFactory,
            long gasEstimationCap) {
        this.chainId = chainId;
        this.blockchain = blockchain;
        this.transactionPool = transactionPool;
        this.reversibleTransactionExecutor = reversibleTransactionExecutor;
        this.executionBlockRetriever = executionBlockRetriever;
        this.repositoryLocator = repositoryLocator;
        this.ethModuleWallet = ethModuleWallet;
        this.ethModuleTransaction = ethModuleTransaction;
        this.bridgeConstants = bridgeConstants;
        this.bridgeSupportFactory = bridgeSupportFactory;
        this.gasEstimationCap = gasEstimationCap;
    }

    @Override
    public String[] accounts() {
        return ethModuleWallet.accounts();
    }

    public Map<String, Object> bridgeState() throws IOException, BlockStoreException {
        Block bestBlock = blockchain.getBestBlock();
        Repository track = repositoryLocator.startTrackingAt(bestBlock.getHeader());

        BridgeSupport bridgeSupport = bridgeSupportFactory.newInstance(
                track, bestBlock, PrecompiledContracts.BRIDGE_ADDR, null);

        byte[] result = bridgeSupport.getStateForDebugging();

        BridgeState state = BridgeState.create(bridgeConstants, result, null);

        return state.stateToMap();
    }

    public String call(CallArguments args, String bnOrId) {
        String hReturn = null;
        try {
            BlockResult blockResult = executionBlockRetriever.retrieveExecutionBlock(bnOrId);
            ProgramResult res;
            if (blockResult.getFinalState() != null) {
                res = callConstant_workaround(args, blockResult);
            } else {
                res = callConstant(args, blockResult.getBlock());
            }

            if (res.isRevert()) {
                Optional<String> revertReason = decodeRevertReason(res);
                if (revertReason.isPresent()) {
                    throw RskJsonRpcRequestException.transactionRevertedExecutionError(revertReason.get());
                } else {
                    throw RskJsonRpcRequestException.transactionRevertedExecutionError();
                }
            }

            hReturn = toUnformattedJsonHex(res.getHReturn());

            return hReturn;
        } finally {
            LOGGER.debug("eth_call(): {}", hReturn);
        }
    }

    public String estimateGas(CallArguments args) {
        String estimation = null;
        Block bestBlock = blockchain.getBestBlock();
        try {
            CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args);

            TransactionExecutor executor = reversibleTransactionExecutor.estimateGas(
                    bestBlock,
                    bestBlock.getCoinbase(),
                    hexArgs.getGasPrice(),
                    hexArgs.gasLimitForGasEstimation(gasEstimationCap),
                    hexArgs.getToAddress(),
                    hexArgs.getValue(),
                    hexArgs.getData(),
                    hexArgs.getFromAddress()
            );
            // gasUsed cannot be greater than the gas passed, which should not
            // be higher than the block gas limit, so we don't expect any overflow
            // in these operations unless the user provides a malicius gasLimit value.

            ProgramResult programResult = executor.getResult();

            long newGasNeeded = programResult.movedRemainingGasToChild() ?
                    programResult.getGasUsed() + programResult.getDeductedRefund() : // then we need to count the deductedRefunds
                    programResult.getMaxGasUsed(); // because deductedRefund can never be higher than gasUsed/2, we can just take the relative upper bound

            estimation = TypeConverter.toQuantityJsonHex(newGasNeeded);
            setEstimationResult(programResult);

            return estimation;
        } finally {
            LOGGER.debug("eth_estimateGas(): {}", estimation);
        }
    }
    private static final Logger LOGGER_FEDE = LoggerFactory.getLogger("fede");

    @Override
    public String sendTransaction(CallArguments args) {
        return ethModuleTransaction.sendTransaction(args);
    }

    @Override
    public String sendRawTransaction(String rawData) {
        return ethModuleTransaction.sendRawTransaction(rawData);
    }

    @Override
    public String sign(String addr, String data) {
        return ethModuleWallet.sign(addr, data);
    }

    public String chainId() {
        return TypeConverter.toJsonHex(new byte[] { chainId });
    }

    public String getCode(String address, String blockId) {
        if (blockId == null) {
            throw new NullPointerException();
        }

        String s = null;
        try {
            RskAddress addr = new RskAddress(address);

            AccountInformationProvider accountInformationProvider = getAccountInformationProvider(blockId);

            if(accountInformationProvider != null) {
                byte[] code = accountInformationProvider.getCode(addr);

                // Code can be null, if there is no account.
                if (code == null) {
                    code = new byte[0];
                }

                s = toUnformattedJsonHex(code);
            }

            return s;
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("eth_getCode({}, {}): {}", address, blockId, s);
            }
        }
    }

    private AccountInformationProvider getAccountInformationProvider(String id) {
        switch (id.toLowerCase()) {
            case "pending":
                return transactionPool.getPendingState();
            case "earliest":
                return repositoryLocator.snapshotAt(blockchain.getBlockByNumber(0).getHeader());
            case "latest":
                return repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());
            default:
                try {
                    long blockNumber = stringHexToBigInteger(id).longValue();
                    Block requestedBlock = blockchain.getBlockByNumber(blockNumber);
                    if (requestedBlock != null) {
                        return repositoryLocator.snapshotAt(requestedBlock.getHeader());
                    }
                    return null;
                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    throw invalidParamError("invalid blocknumber " + id);
                }
        }
    }

    @VisibleForTesting
    public ProgramResult callConstant(CallArguments args, Block executionBlock) {
        CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args);
        return reversibleTransactionExecutor.executeTransaction(
                executionBlock,
                executionBlock.getCoinbase(),
                hexArgs.getGasPrice(),
                hexArgs.getGasLimit(),
                hexArgs.getToAddress(),
                hexArgs.getValue(),
                hexArgs.getData(),
                hexArgs.getFromAddress()
        );
    }

    /**
     * Look for { Error("msg") } function, if it matches decode the "msg" param.
     * The 4 first bytes are the function signature.
     *
     * @param res
     * @return revert reason, empty if didnt match.
     */
    public static Optional<String> decodeRevertReason(ProgramResult res) {
        byte[] bytes = res.getHReturn();
        if (bytes == null || bytes.length < 4) {
            return Optional.empty();
        }

        final byte[] signature = copyOfRange(res.getHReturn(), 0, 4);
        if (!Arrays.equals(signature, ERROR_ABI_FUNCTION_SIGNATURE)) {
            return Optional.empty();
        }

        final Object[] decode = ERROR_ABI_FUNCTION.decode(res.getHReturn());
        return decode != null && decode.length > 0 ? Optional.of((String) decode[0]) : Optional.empty();
    }

    @Deprecated
    private ProgramResult callConstant_workaround(CallArguments args, BlockResult executionBlock) {
        CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args);
        return reversibleTransactionExecutor.executeTransaction_workaround(
                new MutableRepository(new TrieStoreImpl(new HashMapDB()), executionBlock.getFinalState()),
                executionBlock.getBlock(),
                executionBlock.getBlock().getCoinbase(),
                hexArgs.getGasPrice(),
                hexArgs.getGasLimit(),
                hexArgs.getToAddress(),
                hexArgs.getValue(),
                hexArgs.getData(),
                hexArgs.getFromAddress()
        );
    }

    public ProgramResult getEstimationResult() {
        return estimationResult;
    }

    public void setEstimationResult(ProgramResult estimationResult) {
        this.estimationResult = estimationResult;
    }
}
