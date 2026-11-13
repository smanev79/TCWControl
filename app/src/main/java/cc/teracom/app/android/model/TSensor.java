// Same class name, fields and serialVersionUID as the original Java TCW Control app, so its
// saved state file (teracom.state) can still be read. Do not rename or change.
package cc.teracom.app.android.model;

import java.io.Serializable;

public class TSensor implements Serializable {
    private static final long serialVersionUID = -7408959102970331839L;

    private boolean visible = true;
    private String description;

    public boolean isVisible() { return visible; }
    public String getDescription() { return description; }
}
