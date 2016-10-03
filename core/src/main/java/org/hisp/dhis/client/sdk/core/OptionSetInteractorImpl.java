package org.hisp.dhis.client.sdk.core;

public class OptionSetInteractorImpl {
    private final OptionSetStore optionSetStore;
    private final OptionSetApi optionSetApi;

    public OptionSetInteractorImpl(OptionSetStore optionSetStore, OptionSetApi optionSetApi) {
        this.optionSetStore = optionSetStore;
        this.optionSetApi = optionSetApi;
    }

    public OptionSetStore store() {
        return optionSetStore;
    }

    public OptionSetApi api() {
        return optionSetApi;
    }
}
