package com.example.gsb.pbft;

/** 集群级运行统计（所有计数器只增不减，供测试断言）。 */
public final class Stats {

    private long prePrepareSent;
    private long prepareSent;
    private long commitSent;
    private long viewChangeSent;
    private long newViewSent;
    private long dedupDrops;
    private long invalidSignatureDrops;
    private long conflictDrops;
    private long staleDrops;
    private long viewChanges;
    private long finalizedRequests;

    void recordSent(Message.Type type) {
        switch (type) {
            case PRE_PREPARE -> prePrepareSent++;
            case PREPARE -> prepareSent++;
            case COMMIT -> commitSent++;
            case VIEW_CHANGE -> viewChangeSent++;
            case NEW_VIEW -> newViewSent++;
        }
    }

    void dedupDrop() {
        dedupDrops++;
    }

    void invalidSignatureDrop() {
        invalidSignatureDrops++;
    }

    void conflictDrop() {
        conflictDrops++;
    }

    void staleDrop() {
        staleDrops++;
    }

    void viewChangeCompleted() {
        viewChanges++;
    }

    void requestFinalized() {
        finalizedRequests++;
    }

    public long prePrepareSent() {
        return prePrepareSent;
    }

    public long prepareSent() {
        return prepareSent;
    }

    public long commitSent() {
        return commitSent;
    }

    public long viewChangeSent() {
        return viewChangeSent;
    }

    public long newViewSent() {
        return newViewSent;
    }

    public long dedupDrops() {
        return dedupDrops;
    }

    public long invalidSignatureDrops() {
        return invalidSignatureDrops;
    }

    public long conflictDrops() {
        return conflictDrops;
    }

    public long staleDrops() {
        return staleDrops;
    }

    public long viewChanges() {
        return viewChanges;
    }

    public long finalizedRequests() {
        return finalizedRequests;
    }
}
