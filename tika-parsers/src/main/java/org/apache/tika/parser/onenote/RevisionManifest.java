package org.apache.tika.parser.onenote;

public class RevisionManifest {
    ExtendedGUID ridDependent;
    long timeCreation; //ignored
    long revisionRole;
    long odcsDefault;

    public ExtendedGUID getRidDependent() {
        return ridDependent;
    }

    public RevisionManifest setRidDependent(ExtendedGUID ridDependent) {
        this.ridDependent = ridDependent;
        return this;
    }

    public long getTimeCreation() {
        return timeCreation;
    }

    public RevisionManifest setTimeCreation(long timeCreation) {
        this.timeCreation = timeCreation;
        return this;
    }

    public long getRevisionRole() {
        return revisionRole;
    }

    public RevisionManifest setRevisionRole(long revisionRole) {
        this.revisionRole = revisionRole;
        return this;
    }

    public long getOdcsDefault() {
        return odcsDefault;
    }

    public RevisionManifest setOdcsDefault(long odcsDefault) {
        this.odcsDefault = odcsDefault;
        return this;
    }
}
