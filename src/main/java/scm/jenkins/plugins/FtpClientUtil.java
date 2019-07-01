package scm.jenkins.plugins;

import java.io.IOException;
import java.net.SocketException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class FtpClientUtil {

    public static FTPClient getFtpClient(String ip, String port, String username, String password)
            throws NumberFormatException, SocketException, IOException {
        FTPClient ftpClient = null;
        ftpClient = new FTPClient();
        ftpClient.connect(ip, Integer.parseInt(port));
        ftpClient.login(username, password);
        ftpClient.setControlEncoding("UTF-8");
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
        ftpClient.enterLocalPassiveMode();
        if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
            ftpClient.disconnect();
            throw new IOException("Connection is failed,wrong username or password");
        }
        return ftpClient;
    }

}
