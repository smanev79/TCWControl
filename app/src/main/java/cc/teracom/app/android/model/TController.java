// Same class name, fields and serialVersionUID as the original Java TCW Control app, so its
// saved state file (teracom.state) can still be read. Do not rename or change.
package cc.teracom.app.android.model;

import java.io.Serializable;
import java.util.Vector;

public class TController implements Serializable {
    private static final long serialVersionUID = 7112282351631604684L;

    private String deviceName;
    private String deviceIp;
    private String devicePort;
    private String location;
    private String username;
    private String password;
    private Vector<TSensor> all_sensors;

    public String getDeviceName() { return deviceName; }
    public String getDeviceIp() { return deviceIp; }
    public String getDevicePort() { return devicePort; }
    public String getLocation() { return location; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Vector<TSensor> getAllSensors() { return all_sensors; }
}
