package dev.sobatista.fidus.cluster;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.TransferRequest;

import java.io.Serializable;

/**
 * A state-changing notary operation, carried end-to-end through Byzantine reliable broadcast so that every correct
 * replica applies exactly the same thing. Each variant carries the original signed client request(s), so a replica
 * re-checks authenticity itself rather than trusting whoever relayed it.
 */
public sealed interface BftOperation extends Serializable permits BftOperation.Sell, BftOperation.Transfer {

    record Sell(Signed<SellRequest> request) implements BftOperation {}

    record Transfer(Signed<TransferRequest> sellerRequest, Signed<BuyRequest> buyerRequest) implements BftOperation {}
}
