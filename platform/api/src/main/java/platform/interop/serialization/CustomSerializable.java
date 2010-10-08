package platform.interop.serialization;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface CustomSerializable<P extends SerializationPool> {
    public void customSerialize(P pool, DataOutputStream outStream, String serializationType) throws IOException;
    public void customDeserialize(P pool, int iID, DataInputStream inStream) throws IOException;
}
