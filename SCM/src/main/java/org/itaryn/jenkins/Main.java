package org.itaryn.jenkins;

import com.cdancy.jenkins.rest.JenkinsClient;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class Main {
    public static void main(String[] args) throws IOException {
        String endpoint = args[0];
        String username = args[1];
        String password = args[2];
        String credentialsId = args[3];
        String gitUrl = args[4];
        String gitUsername = args[5];
        String gitPassword = args[6];
        String branch = args[7];

        JenkinsClient client = JenkinsClient.builder()
                .endPoint(endpoint)
                .credentials(String.format("%s:%s", username, password))
                .build();

        ClassLoader classLoader = Main.class.getClassLoader();
        String jobXmlTemplate;
        try {
            URL resource = classLoader.getResource("job.xml");
            jobXmlTemplate = Files.readString(Path.of(resource.toURI()))
                    .replace("GIT_URL", gitUrl)
                    .replace("CREDENTIALS_ID", credentialsId)
                    .replace("BRANCH", branch);
        } catch (URISyntaxException | IOException e) {
            System.err.println("Can't find the job.xml default file in jar");
            System.err.println(Arrays.toString(e.getStackTrace()));
            return;
        }

        Map<Path, List<Path>> folders = Map.of();

        Path tempFolder = Files.createTempDirectory("");
        try (Git git = Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(tempFolder.toFile())
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitUsername, gitPassword))
                .call()) {
            try (Stream<Path> stream = Files.walk(tempFolder, 2)) {
                folders = stream
                        .filter(file -> !Files.isDirectory(file))
                        .filter(file -> file.getFileName().toString().endsWith(".groovy"))
                        .collect(groupingBy(file -> file.getName(file.getNameCount() - 2)));
            }
        } catch (GitAPIException | IOException e) {
            System.err.println("Error while getting information on git");
            System.err.println(Arrays.toString(e.getStackTrace()));
        } finally {
            FileUtils.deleteDirectory(tempFolder.toFile());
        }

        client.api().jobsApi().create("test", "mytest", jobXmlTemplate);

        folders.forEach((folder, files) -> {
            files.forEach(file -> {
                String jobXml = jobXmlTemplate
                        .replace("PATH", String.format("%s/%s", folder, file.getFileName()));
                String jobName = String.format("%s - %s", folder, file.getFileName().toString().replace("_", " ").replace(".groovy", ""));
                System.out.printf("Creating job %s%n", jobName);
                client.api().jobsApi().create(null, jobName, jobXml);
            });
        });

        client.close();
    }
}