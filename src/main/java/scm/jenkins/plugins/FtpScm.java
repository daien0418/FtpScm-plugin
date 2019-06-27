package scm.jenkins.plugins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.kohsuke.stapler.DataBoundConstructor;

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
import io.jenkins.plugins.sample.Messages;

public class FtpScm extends SCM {

    public static final String PATTERN = "(2[0-5]{2}|[0-1]?\\d{1,2})(\\.(2[0-5]{2}|[0-1]?\\d{1,2})){3}";

    @CheckForNull
    public final String host;
    @CheckForNull
    public final String port;
    @CheckForNull
    public final String userName;
    @CheckForNull
    public final String password;
    public final String ftpPath;
    @CheckForNull
    public final String fileNames;

    public final boolean cleanWorkSpace;

    @DataBoundConstructor
    public FtpScm(@CheckForNull String host, @CheckForNull String port, @CheckForNull String userName,
            @CheckForNull String password, String ftpPath, @CheckForNull String fileNames, boolean cleanWorkSpace) {
        this.host = host;
        this.port = port;
        this.userName = userName;
        this.password = password;
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

        listener.getLogger().println("File list of current workspace:");
        List<FilePath> filePaths = workspace.list();
        for (FilePath filePath : filePaths) {
            listener.getLogger().println(filePath.getName());
        }

        // listener.getLogger().println("host: " + this.host);
        // listener.getLogger().println("port: " + this.port);
        // listener.getLogger().println("userName: " + this.userName);
        // listener.getLogger().println("password: " + this.password);
        // listener.getLogger().println("ftpPath: " + this.ftpPath);
        // listener.getLogger().println("fileNames: " + this.fileNames);

        Pattern r = Pattern.compile(PATTERN);
        Matcher m = r.matcher(this.host);
        if (!m.matches()) {
            listener.getLogger().println(Messages.FtpScm_errors_host());
            throw new UnknownHostException();
        }

        try {
            Integer.parseInt(port);
        } catch (NumberFormatException e) {
            listener.getLogger().println(Messages.FtpScm_errors_port());
            throw e;
        }

        if (this.fileNames == null || this.fileNames.trim().equals("")) {
            listener.getLogger().println(Messages.FtpScm_errors_fileNames());
            throw new NullPointerException();
        }

        if (cleanWorkSpace) {
            listener.getLogger().println("Start cleaning the workspace...");
            workspace.deleteContents();
        }

        downloadFtpFiles(listener, this.host, this.userName, this.password, Integer.parseInt(this.port), this.ftpPath,
                workspace.getRemote(), this.fileNames.split(","));
    }

    public void downloadFtpFiles(TaskListener listener, String ftpHost, String ftpUserName, String ftpPassword,
            int ftpPort, String ftpPath, String localPath, String[] fileNames) throws IOException {

        File dir = new File(localPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        FTPClient ftpClient = null;
        boolean isDownloadSuccess = false;

        try {
            ftpClient = getFTPClient(listener, ftpHost, ftpUserName, ftpPassword, ftpPort);
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

    public FTPClient getFTPClient(TaskListener listener, String ftpHost, String ftpUserName, String ftpPassword,
            int ftpPort) throws IOException {
        listener.getLogger().println("Start connecting ftp server..");
        FTPClient ftpClient = null;
        try {
            ftpClient = new FTPClient();
            ftpClient.connect(ftpHost, ftpPort);
            ftpClient.login(ftpUserName, ftpPassword);
            ftpClient.setControlEncoding("UTF-8");
            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();
            if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
                listener.getLogger().println(Messages.FtpScm_errors_credentials());
                ftpClient.disconnect();
                throw new IOException(Messages.FtpScm_errors_credentials());
            } else {
                listener.getLogger().println("Connect success");
            }
        } catch (SocketException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        }
        return ftpClient;
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

    }

}
