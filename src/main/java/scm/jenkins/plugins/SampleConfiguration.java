package scm.jenkins.plugins;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * Example of Jenkins global configuration.
 */
@Extension
public class SampleConfiguration extends GlobalConfiguration {

    public static final String PATTERN = "(2[0-5]{2}|[0-1]?\\d{1,2})(\\.(2[0-5]{2}|[0-1]?\\d{1,2})){3}";

    @CopyOnWrite
    public volatile FtpInstallation[] installations = new FtpInstallation[0];

    /** @return the singleton instance */
    public static SampleConfiguration get() {
        return GlobalConfiguration.all().get(SampleConfiguration.class);
    }

    public SampleConfiguration() {
        // When Jenkins is restarted, load any saved configuration from disk.
        load();
    }

    public FtpInstallation[] getInstallations() {
        return installations;
    }

    public void setInstallations(FtpInstallation[] installations) {
        this.installations = installations;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        List<FtpInstallation> list = req.bindJSONToList(FtpInstallation.class, json.get("inst"));
        List<FtpInstallation> installations = new ArrayList<FtpInstallation>();
        for (FtpInstallation ftpInstallation : list) {
            if (StringUtils.isEmpty(ftpInstallation.name)) {
                continue;
            } else {
                installations.add(ftpInstallation);
            }
        }

        setInstallations(installations.toArray(new FtpInstallation[installations.size()]));
        return true;
    }

    public FormValidation doCheckMandatory(@QueryParameter String value) {
        return StringUtils.isBlank(value) ? FormValidation.error("Name can't be empty") : FormValidation.ok();
    }

    public FormValidation doCheckIp(@QueryParameter String value) {

        if (StringUtils.isBlank(value)) {
            return FormValidation.error("Ip can't be empty");
        }

        Pattern r = Pattern.compile(PATTERN);
        Matcher m = r.matcher(value);
        if (!m.matches()) {
            return FormValidation.error("Ip is invalid");
        }

        return FormValidation.ok();
    }

    public FormValidation doCheckPort(@QueryParameter String value) {

        if (StringUtils.isBlank(value)) {
            return FormValidation.error("Port can't be empty");
        }

        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return FormValidation.error("Port is invalid");
        }

        return FormValidation.ok();
    }

    @RequirePOST
    @Restricted(DoNotUse.class) // WebOnly
    public FormValidation doTestConnection(@QueryParameter("ip") String ip, @QueryParameter("port") String port,
            @QueryParameter("credentialsId") String credentialsId) {
        Jenkins.getActiveInstance().checkPermission(Jenkins.ADMINISTER);

        FormValidation ipFormValidation = doCheckIp(ip);
        if (!ipFormValidation.equals(FormValidation.ok())) {
            return ipFormValidation;
        }

        FormValidation portFormValidation = doCheckPort(port);
        if (!portFormValidation.equals(FormValidation.ok())) {
            return portFormValidation;
        }

        StandardUsernamePasswordCredentials credentials = getStandardUserAndPwdCred(credentialsId);
        String username = credentials != null ? credentials.getUsername() : "";
        String password = credentials != null ? credentials.getPassword().getPlainText() : "";

        try {
            FtpClientUtil.getFtpClient(ip, port, username, password);
        } catch (NumberFormatException | IOException e) {
            return FormValidation.error(e.getMessage());
        }

        return FormValidation.ok("Connection success");
    }

    public static StandardUsernamePasswordCredentials getStandardUserAndPwdCred(String credentialsId) {
        return CredentialsMatchers
                .firstOrNull(
                        CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, (Item) null,
                                ACL.SYSTEM, new ArrayList<DomainRequirement>()),
                        CredentialsMatchers.withId(credentialsId));
    }

    public ListBoxModel doFillCredentialsIdItems(@QueryParameter("credentialsId") String credentialsId) {
        System.out.println("credentialsId: " + credentialsId);
        if (Jenkins.getInstance().hasPermission(Item.CONFIGURE)) {
            StandardListBoxModel options = (StandardListBoxModel) new StandardListBoxModel().withEmptySelection()
                    .withMatching(new FtpCredentialMatcher(),
                            CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class));
            options.includeCurrentValue(credentialsId);
            return options;
        }
        return new StandardListBoxModel();
    }

    private static class FtpCredentialMatcher implements CredentialsMatcher {
        @Override
        public boolean matches(@NonNull Credentials credentials) {
            try {
                return credentials instanceof StandardUsernamePasswordCredentials;
            } catch (Throwable e) {
                return false;
            }
        }
    }

}
