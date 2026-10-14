package com.friendlystation.platform.auth;

import com.friendlystation.platform.workflow.DemoWorkflowStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 内存 profile 下的凭据版本：存在 DemoWorkflowStore 的内存 Map。 */
@Service
@Profile("!mysql")
public class DemoCredentialVersionService implements CredentialVersionService {

    private final DemoWorkflowStore store;

    public DemoCredentialVersionService(DemoWorkflowStore store) {
        this.store = store;
    }

    @Override
    public int versionOf(long userId) {
        return store.credentialVersionOf(userId);
    }

    @Override
    public void bump(long userId) {
        store.bumpCredentialVersion(userId);
    }
}
