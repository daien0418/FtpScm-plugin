package io.jenkins.plugins;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

public class FtpInstallation implements Serializable {

    private static final long serialVersionUID = 1L;

    public String name;

    public String ip;

    public String port;

    public String credentialsId;

    public FtpInstallation() {}

    @DataBoundConstructor
    public FtpInstallation(String name, String ip, String port, String credentialsId) {
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

    public void setName(String name) {
        this.name = name;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

}
