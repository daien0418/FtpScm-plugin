package io.jenkins.plugins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.List;

import org.apache.commons.net.ftp.FTPClient;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.NullChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.ListBoxModel;

public class FtpScm extends SCM {

    public final String ftpServer;

    public final String ftpPath;
    @CheckForNull
    public final String fileNames;

    public final boolean cleanWorkSpace;

    @DataBoundConstructor
    public FtpScm(@CheckForNull String ftpServer, String ftpPath, @CheckForNull String fileNames,
            boolean cleanWorkSpace) {
        this.ftpServer = ftpServer;
        this.ftpPath = ftpPath;
        this.fileNames = fileNames;
        this.cleanWorkSpace = cleanWorkSpace;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build, FilePath workspace, Launcher launcher,
            TaskListener listener) throws IOException, InterruptedException {
        return null;
    }

    @Override
    public PollingResult compareRemoteRevisionWith(Job<?, ?> project, Launcher launcher, FilePath workspace,
            TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        return PollingResult.NO_CHANGES;
    }

    @Override
    public void checkout(Run<?, ?> build, Launcher launcher, FilePath workspace, TaskListener listener,
            File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
        if (changelogFile != null) {
            this.createEmptyChangeLog(changelogFile, listener, "log");
        }

        listener.getLogger().println("File list of current workspace...");
        List<FilePath> filePaths = workspace.list();
        for (FilePath filePath : filePaths) {
            listener.getLogger().println(filePath.getName());
        }

        FtpInstallation[] ftpInstallations = FtpGlobalConfiguration.get().getInstallations();
        FtpInstallation ftpServer = null;
        for (FtpInstallation ftpInstallation : ftpInstallations) {
            if (ftpInstallation.name.equals(this.ftpServer)) {
                ftpServer = ftpInstallation;
            }
        }

        if (ftpServer == null) {
            throw new IOException("No available ftpServer");
        }

        StandardUsernamePasswordCredentials credentials = FtpGlobalConfiguration.getStandardUserAndPwdCred(ftpServer.credentialsId);
        String username = credentials == null ? "" : credentials.getUsername();
        String password = credentials == null ? "" : credentials.getPassword().getPlainText();

        FTPClient ftpClient = null;
        try {
            ftpClient = FtpClientUtil.getFtpClient(ftpServer.ip, ftpServer.port, username, password);
        } catch (Exception e) {
            listener.getLogger()
                    .println(String.format("Can't connect to the ftpServer: %s:%s", ftpServer.ip, ftpServer.port));
            throw e;
        }

        if (cleanWorkSpace) {
            listener.getLogger().println("Start cleaning the workspace...");
            workspace.deleteContents();
        }

        downloadFtpFiles(listener, ftpClient, this.ftpPath, workspace.getRemote(), this.fileNames.split(","));
    }

    public void downloadFtpFiles(TaskListener listener, FTPClient ftpClient, String ftpPath, String localPath,
            String[] fileNames) throws IOException {
        File dir = new File(localPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        boolean isDownloadSuccess = false;
        try {
            ftpClient.setControlEncoding("UTF-8");
            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();
            ftpClient.changeWorkingDirectory(ftpPath);

            for (String fileName : fileNames) {
                fileName = fileName.trim();
                if (fileName == null || fileName.trim().equals("")) {
                    continue;
                }
                listener.getLogger().println("Start downloading file: " + fileName);

                File localFile = new File(localPath + File.separatorChar + fileName);
                try (OutputStream os = new FileOutputStream(localFile);) {
                    isDownloadSuccess = ftpClient.retrieveFile(fileName, os);
                } catch (IOException e) {
                    throw e;
                }

                if (!isDownloadSuccess) {
                    listener.getLogger().println("Failed download file: " + fileName);
                    throw new IOException("Failed download file: " + fileName);
                } else {
                    listener.getLogger().println("Successfully download file: " + fileName);
                }
            }
        } catch (SocketException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        }
        finally {
            ftpClient.logout();
        }
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return NullChangeLogParser.INSTANCE;
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<FtpScm> {

        public DescriptorImpl() {
            super((Class) null);
        }

        @Override
        public String getDisplayName() {
            return "Ftp";
        }

        @Override
        public boolean isApplicable(Job project) {
            return true;
        }

        public ListBoxModel doFillFtpServerItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.FtpScm_info_ftpserver(), "");
            for (FtpInstallation ftpInstallation : FtpGlobalConfiguration.get().getInstallations()) {
                model.add(ftpInstallation.name);
            }
            return model;
        }

    }

}
