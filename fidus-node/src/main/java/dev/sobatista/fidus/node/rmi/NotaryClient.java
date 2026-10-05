package dev.sobatista.fidus.node.rmi;

import dev.sobatista.fidus.crypto.Signable;
import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.GoodState;
import dev.sobatista.fidus.node.Bootstrap;
import dev.sobatista.fidus.node.ProofOfWork;

import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.PrivateKey;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A client/user library over RMI: it signs each request and mints the Proof of Work the server demands, then calls
 * the remote notary. The {@code main} drives a scripted transaction (alice sells G1, bob buys it, carol reads it).
 */
public final class NotaryClient {
    private final NotaryService notary;
    private final Bootstrap world;
    private final int difficulty;

    public NotaryClient(NotaryService notary, Bootstrap world, int difficulty) {
        this.notary = notary;
        this.world = world;
        this.difficulty = difficulty;
    }

    private <T extends Signable> Signed<T> sign(T payload, String id) {
        return Signed.sign(payload, id, world.privateKey(id));
    }

    public Signed<GoodState> intentionToSell(String owner, String good, long seq) throws Exception {
        Signed<SellRequest> req = sign(new SellRequest(owner, good, seq), owner);
        return notary.intentionToSell(req, ProofOfWork.mint(req.payload().canonicalBytes(), difficulty));
    }

    public Signed<TransferCertificate> buy(String buyer, String seller, String good, long sellerSeq) throws Exception {
        Signed<BuyRequest> buyReq = sign(new BuyRequest(buyer, seller, good, rand()), buyer);
        Signed<TransferRequest> sellReq = sign(new TransferRequest(seller, buyer, good, sellerSeq), seller);
        return notary.transferGood(sellReq, buyReq, ProofOfWork.mint(sellReq.payload().canonicalBytes(), difficulty));
    }

    public Signed<GoodState> stateOfGood(String reader, String good) throws Exception {
        return notary.stateOfGood(sign(new StateRequest(reader, good, rand()), reader));
    }

    private static long rand() {
        return ThreadLocalRandom.current().nextLong();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: NotaryClient <storage-dir> [pow-difficulty]");
            return;
        }
        Path storage = Path.of(args[0]);
        int difficulty = args.length > 1 ? Integer.parseInt(args[1]) : 18;

        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        NotaryService service = (NotaryService) registry.lookup(NotaryService.NAME);
        NotaryClient client = new NotaryClient(service, new Bootstrap(storage), difficulty);

        client.intentionToSell("alice", "G1", 1);
        System.out.println("alice put G1 on sale");
        Signed<TransferCertificate> cert = client.buy("bob", "alice", "G1", 2);
        System.out.println("bob bought G1; certificate verifies: " + cert.verify(new Bootstrap(storage).directory().publicKey(service.id())));
        GoodState state = client.stateOfGood("carol", "G1").payload();
        System.out.println("G1 is now owned by " + state.ownerId());
    }
}
