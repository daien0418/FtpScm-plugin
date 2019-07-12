package io.jenkins.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
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
import hudson.model.Api;
import hudson.model.Item;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.util.JSONUtils;

/**
 * Example of Jenkins global configuration.
 */
@Extension
public class FtpGlobalConfiguration extends GlobalConfiguration implements UnprotectedRootAction {

    public static final String PATTERN = "(2[0-5]{2}|[0-1]?\\d{1,2})(\\.(2[0-5]{2}|[0-1]?\\d{1,2})){3}";

    @CopyOnWrite
    public volatile FtpInstallation[] installations = new FtpInstallation[0];

    /** @return the singleton instance */
    public static FtpGlobalConfiguration get() {
        return GlobalConfiguration.all().get(FtpGlobalConfiguration.class);
    }

    public FtpGlobalConfiguration() {
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

    public FormValidation doCheckName(@QueryParameter String value) {
        return StringUtils.isBlank(value) ? FormValidation.error(Messages.FtpScm_errors_name()) : FormValidation.ok();
    }

    public FormValidation doCheckIp(@QueryParameter String value) {

        if (StringUtils.isBlank(value)) {
            return FormValidation.error(Messages.FtpScm_errors_ip_empty());
        }

        Pattern r = Pattern.compile(PATTERN);
        Matcher m = r.matcher(value);
        if (!m.matches()) {
            return FormValidation.error(Messages.FtpScm_errors_ip_invalid());
        }

        return FormValidation.ok();
    }

    public FormValidation doCheckPort(@QueryParameter String value) {

        if (StringUtils.isBlank(value)) {
            return FormValidation.error(Messages.FtpScm_errors_port_empty());
        }

        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return FormValidation.error(Messages.FtpScm_errors_port_invalid());
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

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "createFtp";
    }

    public Api getApi() {
        return new Api(this);
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // stapler web method
    public HttpResponse doCreate(StaplerRequest req) throws ServletException, IOException {
        StringBuilder stringBuilder = new StringBuilder();
        String line = "";
        while ((line = req.getReader().readLine()) != null) {
            stringBuilder.append(line);
            System.out.println(line);
        }

        JSONObject jsonObject = JSONObject.fromObject(stringBuilder.toString());
        FtpInstallation ftpInstallation = (FtpInstallation) JSONObject.toBean(jsonObject, FtpInstallation.class);

        StandardListBoxModel options = (StandardListBoxModel) new StandardListBoxModel().withEmptySelection()
                .withMatching(new FtpCredentialMatcher(),
                        CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class));

        boolean contain = false;
        Iterator<Option> iterator = options.iterator();
        while (iterator.hasNext()) {
            Option op = iterator.next();
            if (op.value.equals(ftpInstallation.credentialsId)) {
                contain = true;
                break;
            }
        }

        if (ftpInstallation.credentialsId.trim().equals("")) {
            contain = true;
        }

        if (!contain) {
            return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST,"credentialId does not exists");
        }

        List<FtpInstallation> list = new ArrayList<FtpInstallation>();
        for (FtpInstallation ftp : this.installations) {
            list.add(ftp);
        }
        list.add(ftpInstallation);

        FtpInstallation[] ftps = list.toArray(new FtpInstallation[list.size()]);
        setInstallations(ftps);

        return HttpResponses.ok();
    }

}
