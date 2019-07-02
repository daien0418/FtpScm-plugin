package scm.jenkins.plugins;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

public class FtpInstallation implements Serializable{

    private static final long serialVersionUID = 1L;

    public final String name;

    public final String ip;

    public final String port;

    public final String credentialsId;

    @DataBoundConstructor
    public FtpInstallation(String name, String ip, String port,String credentialsId) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.credentialsId = credentialsId;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public String getPort() {
        return port;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

}
