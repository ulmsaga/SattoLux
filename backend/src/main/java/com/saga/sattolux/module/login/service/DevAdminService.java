package com.saga.sattolux.module.login.service;

import com.saga.sattolux.module.login.dto.DevUserEnsureResponse;
import com.saga.sattolux.module.login.dto.DevLoginAccountSyncResponse;

public interface DevAdminService {
    DevUserEnsureResponse ensureGeneralUser();
    DevLoginAccountSyncResponse syncLoginAccounts();
}
