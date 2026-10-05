package dev.sobatista.fidus;

import dev.sobatista.fidus.crypto.Signatures;
import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.log.TransactionLog;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.UserDirectory;
import dev.sobatista.fidus.notary.SingleNotary;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

/** Test keys and request builders. 1024-bit RSA keeps the suite fast; production keys are larger. */
final class Fixtures {
    static final String NOTARY = "NOTARY";

    final UserDirectory directory;
    private final Map<String, KeyPair> keys = new HashMap<>();

    Fixtures(String... users) {
        Map<String, PublicKey> pub = new HashMap<>();
        KeyPair nk = Signatures.generateRsa(1024);
        keys.put(NOTARY, nk);
        pub.put(NOTARY, nk.getPublic());
        for (String u : users) {
            KeyPair k = Signatures.generateRsa(1024);
            keys.put(u, k);
            pub.put(u, k.getPublic());
        }
        this.directory = new UserDirectory(pub);
    }

    PrivateKey priv(String id) {
        return keys.get(id).getPrivate();
    }

    PublicKey pub(String id) {
        return keys.get(id).getPublic();
    }

    SingleNotary notary(TransactionLog log, Map<String, String> owners) {
        return new SingleNotary(NOTARY, priv(NOTARY), directory, owners, log);
    }

    Signed<SellRequest> sell(String owner, String good, long seq) {
        return Signed.sign(new SellRequest(owner, good, seq), owner, priv(owner));
    }

    Signed<TransferRequest> transfer(String seller, String buyer, String good, long seq) {
        return Signed.sign(new TransferRequest(seller, buyer, good, seq), seller, priv(seller));
    }

    Signed<BuyRequest> buy(String buyer, String seller, String good, long nonce) {
        return Signed.sign(new BuyRequest(buyer, seller, good, nonce), buyer, priv(buyer));
    }

    Signed<StateRequest> state(String reader, String good, long nonce) {
        return Signed.sign(new StateRequest(reader, good, nonce), reader, priv(reader));
    }
}
