package com.example.gsb.pbft;

/**
 * 运行统计：各阶段消息数、去重丢弃数、验签失败丢弃数、视图更换次数、达成一致的请求数。
 */
public final class Stats {

    private long prePrepareAccepted;
    private long prepareAccepted;
    private long commitAccepted;
    private long viewChangeAccepted;
    private long newViewAccepted;
    private long dedupDropped;
    private long signatureDropped;
    private long viewChanges;
    private long committedRequests;

    public void incPrePrepare() { prePrepareAccepted++; }
    public void incPrepare() { prepareAccepted++; }
    public void incCommit() { commitAccepted++; }
    public void incViewChangeMsg() { viewChangeAccepted++; }
    public void incNewViewMsg() { newViewAccepted++; }
    public void incDedupDropped() { dedupDropped++; }
    public void incSignatureDropped() { signatureDropped++; }
    public void incViewChanges() { viewChanges++; }
    public void incCommitted() { committedRequests++; }

    public long prePrepareAccepted() { return prePrepareAccepted; }
    public long prepareAccepted() { return prepareAccepted; }
    public long commitAccepted() { return commitAccepted; }
    public long viewChangeAccepted() { return viewChangeAccepted; }
    public long newViewAccepted() { return newViewAccepted; }
    public long dedupDropped() { return dedupDropped; }
    public long signatureDropped() { return signatureDropped; }
    public long viewChanges() { return viewChanges; }
    public long committedRequests() { return committedRequests; }

    /** 汇总另一个统计对象（用于集群级聚合）。 */
    public Stats merge(Stats other) {
        Stats sum = new Stats();
        sum.prePrepareAccepted = this.prePrepareAccepted + other.prePrepareAccepted;
        sum.prepareAccepted = this.prepareAccepted + other.prepareAccepted;
        sum.commitAccepted = this.commitAccepted + other.commitAccepted;
        sum.viewChangeAccepted = this.viewChangeAccepted + other.viewChangeAccepted;
        sum.newViewAccepted = this.newViewAccepted + other.newViewAccepted;
        sum.dedupDropped = this.dedupDropped + other.dedupDropped;
        sum.signatureDropped = this.signatureDropped + other.signatureDropped;
        sum.viewChanges = this.viewChanges + other.viewChanges;
        sum.committedRequests = this.committedRequests + other.committedRequests;
        return sum;
    }

    @Override
    public String toString() {
        return "Stats{prePrepare=" + prePrepareAccepted
                + ", prepare=" + prepareAccepted
                + ", commit=" + commitAccepted
                + ", viewChangeMsg=" + viewChangeAccepted
                + ", newViewMsg=" + newViewAccepted
                + ", dedupDropped=" + dedupDropped
                + ", signatureDropped=" + signatureDropped
                + ", viewChanges=" + viewChanges
                + ", committed=" + committedRequests + '}';
    }
}
