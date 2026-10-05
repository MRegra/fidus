package dev.sobatista.fidus.node.rmi;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.log.FileTransactionLog;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.GoodState;
import dev.sobatista.fidus.node.Bootstrap;
import dev.sobatista.fidus.node.GuardedNotary;
import dev.sobatista.fidus.node.ProofOfWork;
import dev.sobatista.fidus.notary.NotaryException;
import dev.sobatista.fidus.notary.SingleNotary;

import java.io.IOException;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * An RMI notary server: a durable {@link SingleNotary} behind a {@link GuardedNotary} Proof-of-Work gate, exported
 * over Java RMI. (This is the single-notary transport; running a whole Byzantine cluster over RMI is on the roadmap.)
 */
public final class NotaryServer extends UnicastRemoteObject implements NotaryService {
    private final GuardedNotary notary;

    public NotaryServer(GuardedNotary notary) throws IOException {
        super(0);
        this.notary = notary;
    }

    @Override
    public String id() {
        return notary.id();
    }

    @Override
    public Signed<GoodState> stateOfGood(Signed<StateRequest> request) throws NotaryException {
        return notary.stateOfGood(request);
    }

    @Override
    public Signed<GoodState> intentionToSell(Signed<SellRequest> request, ProofOfWork.Stamp pow)
            throws NotaryException {
        return notary.intentionToSell(request, pow);
    }

    @Override
    public Signed<TransferCertificate> transferGood(Signed<TransferRequest> sellerRequest,
                                                    Signed<BuyRequest> buyerRequest,
                                                    ProofOfWork.Stamp pow) throws NotaryException {
        return notary.transferGood(sellerRequest, buyerRequest, pow);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: NotaryServer <storage-dir> [pow-difficulty]");
            return;
        }
        Path storage = Path.of(args[0]);
        int difficulty = args.length > 1 ? Integer.parseInt(args[1]) : 18;

        Bootstrap world = new Bootstrap(storage);
        world.createIfAbsent();
        SingleNotary notary = new SingleNotary(Bootstrap.NOTARY_ID, world.privateKey(Bootstrap.NOTARY_ID),
                world.directory(), world.owners(), new FileTransactionLog(world.logFile()));
        NotaryServer server = new NotaryServer(new GuardedNotary(notary, difficulty));

        Registry registry;
        try {
            registry = LocateRegistry.createRegistry(1099);
        } catch (IOException alreadyRunning) {
            registry = LocateRegistry.getRegistry(1099);
        }
        registry.rebind(NotaryService.NAME, server);
        System.out.printf("fidus notary '%s' ready on rmi://localhost:1099/%s (PoW difficulty %d)%n",
                notary.id(), NotaryService.NAME, difficulty);
    }
}
