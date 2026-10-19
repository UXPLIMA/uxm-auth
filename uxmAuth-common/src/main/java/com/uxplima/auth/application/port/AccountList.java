package com.uxplima.auth.application.port;

import java.util.List;

import com.uxplima.auth.domain.account.Account;

/** Every account at once, for work that goes over all of them, such as a change of UUID scheme. */
@FunctionalInterface
public interface AccountList {

    /** Every account the table holds now. */
    List<Account> all();
}
