package org.apache.tika.parser.onenote;

public class ObjectSpaceObjectPropSet {
    ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs oids = new ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
    ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs osids = new ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
    ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs contextIDs = new ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
    PropertySet body = new PropertySet();

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs getOids() {
        return oids;
    }

    public ObjectSpaceObjectPropSet setOids(ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs oids) {
        this.oids = oids;
        return this;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs getOsids() {
        return osids;
    }

    public ObjectSpaceObjectPropSet setOsids(ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs osids) {
        this.osids = osids;
        return this;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs getContextIDs() {
        return contextIDs;
    }

    public ObjectSpaceObjectPropSet setContextIDs(ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs contextIDs) {
        this.contextIDs = contextIDs;
        return this;
    }

    public PropertySet getBody() {
        return body;
    }

    public ObjectSpaceObjectPropSet setBody(PropertySet body) {
        this.body = body;
        return this;
    }
}
