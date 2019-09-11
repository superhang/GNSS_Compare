//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.galfins.gogpsextracts;

import com.google.location.suplclient.ephemeris.KeplerianEphemeris;

public class BdsEphemeris extends KeplerianEphemeris {
    public final double accuracyM;
    public final double tgdS;
    public final boolean fitIntvFlag;
    public final boolean l2PDataFlag;
    public final int codeL2;
    public final int iodc;

    private BdsEphemeris(BdsEphemeris.Builder builder) {
        super(builder);
        this.accuracyM = builder.accuracyM;
        this.tgdS = builder.tgdS;
        this.fitIntvFlag = builder.fitIntvFlag;
        this.l2PDataFlag = builder.l2PDataFlag;
        this.codeL2 = builder.codeL2;
        this.iodc = builder.iodc;
    }

    public static BdsEphemeris.Builder newBuilder() {
        return new BdsEphemeris.Builder();
    }

    public static class Builder extends com.google.location.suplclient.ephemeris.KeplerianEphemeris.Builder<BdsEphemeris.Builder> {
        private double accuracyM;
        private double tgdS;
        private boolean fitIntvFlag;
        private boolean l2PDataFlag;
        private int codeL2;
        private int iodc;

        private Builder() {
        }

        public BdsEphemeris.Builder getThis() {
            return this;
        }

        public BdsEphemeris.Builder setAccuracyM(double accuracyM) {
            this.accuracyM = accuracyM;
            return this.getThis();
        }

        public BdsEphemeris.Builder setTgdS(double tgdS) {
            this.tgdS = tgdS;
            return this.getThis();
        }

        public BdsEphemeris.Builder setFitIntvFlag(boolean fitIntvFlag) {
            this.fitIntvFlag = fitIntvFlag;
            return this.getThis();
        }

        public BdsEphemeris.Builder setL2PDataFlag(boolean l2PDataFlag) {
            this.l2PDataFlag = l2PDataFlag;
            return this.getThis();
        }

        public BdsEphemeris.Builder setCodeL2(int codeL2) {
            this.codeL2 = codeL2;
            return this.getThis();
        }

        public BdsEphemeris.Builder setIodc(int iodc) {
            this.iodc = iodc;
            return this.getThis();
        }

        public BdsEphemeris build() {
            return new BdsEphemeris(this);
        }
    }
}
