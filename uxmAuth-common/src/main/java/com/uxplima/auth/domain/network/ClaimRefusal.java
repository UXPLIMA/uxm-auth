package com.uxplima.auth.domain.network;

/** Why a member did not take a claim or a report. Each one is its own console line, with what to check. */
public enum ClaimRefusal {
    /** It is not a signed claim at all, or it was cut short. */
    MALFORMED("Check that the proxy and every server run the same uxmAuth."),
    /** It was not signed with this network's secret, or it was changed after it was signed. */
    BAD_SIGNATURE("Check that network.secret is the same on the proxy and on every server."),
    /** It was signed by a release before claims named their server; both sides must run the same one. */
    OUTDATED("The other side runs an older uxmAuth. Update uxmAuth on the proxy and on every server together."),
    /** It was written too long ago, or too far in the future. */
    STALE("Check that the clocks of the proxy and the servers agree."),
    /** It is about another player. */
    NOT_THIS_PLAYER("It names another player than the one it came with."),
    /** It was written for another server, or the report came from a server other than the one it names. */
    WRONG_SERVER("Check that network.server-name on each server is that server's name in the proxy's list."),
    /** It was taken once already: this is a copy of it. */
    REPLAYED("A copy of a claim that was taken already came again; someone may be replaying claims.");

    private final String advice;

    ClaimRefusal(String advice) {
        this.advice = advice;
    }

    /** What the operator should check, for the console line. */
    public String advice() {
        return advice;
    }
}
