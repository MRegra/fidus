package dev.sobatista.fidus.node.rmi;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.GoodState;
import dev.sobatista.fidus.node.ProofOfWork;
import dev.sobatista.fidus.notary.NotaryException;

import java.rmi.Remote;
import java.rmi.RemoteException;

/** The notary as seen over Java RMI. State-changing calls carry a {@link ProofOfWork.Stamp}. */
public interface NotaryService extends Remote {
    String NAME = "fidus-notary";

    String id() throws RemoteException;

    Signed<GoodState> stateOfGood(Signed<StateRequest> request) throws RemoteException, NotaryException;

    Signed<GoodState> intentionToSell(Signed<SellRequest> request, ProofOfWork.Stamp pow)
            throws RemoteException, NotaryException;

    Signed<TransferCertificate> transferGood(Signed<TransferRequest> sellerRequest,
                                             Signed<BuyRequest> buyerRequest,
                                             ProofOfWork.Stamp pow) throws RemoteException, NotaryException;
}
