package org.sopt.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {
  private static final Path SOURCE_ROOT = Path.of("src/main/java/org/sopt");
  private static final Pattern IMPORT = Pattern.compile("(?m)^import (org\\.sopt\\.[^;]+);");

  @Test
  void domainDoesNotDependOnApplicationAdaptersOrAnotherDomain() throws IOException {
    assertNoImports(
        filesIn("member/domain", "post/domain"),
        imported ->
            imported.contains(".application.")
                || imported.contains(".adapter.")
                || imported.startsWith("org.sopt.member.domain") && currentDomain().equals("post")
                || imported.startsWith("org.sopt.post.domain") && currentDomain().equals("member"));
  }

  @Test
  void inputAdaptersDependOnPortsInsteadOfFacadeServicesOrOutputAdapters() throws IOException {
    assertNoImports(
        filesIn("member/adapter/in", "post/adapter/in"),
        imported ->
            imported.contains(".application.facade.")
                || imported.contains(".application.service.")
                || imported.contains(".adapter.out."));
  }

  @Test
  void applicationUsesOtherDomainsOnlyThroughInputPorts() throws IOException {
    assertNoImports(
        filesIn("member/application", "post/application"),
        imported ->
            currentDomain().equals("post")
                    && imported.startsWith("org.sopt.member.")
                    && !imported.startsWith("org.sopt.member.application.port.in.")
                || currentDomain().equals("member") && imported.startsWith("org.sopt.post."));
  }

  @Test
  void globalDoesNotDependOnIndividualDomains() throws IOException {
    assertNoImports(
        filesIn("global"),
        imported ->
            imported.startsWith("org.sopt.member.") || imported.startsWith("org.sopt.post."));
  }

  @Test
  void inputPortsDoNotExposeJpaEntities() throws IOException {
    assertNoImports(
        filesIn("member/application/port/in", "post/application/port/in").stream()
            .filter(file -> file.getFileName().toString().matches(".*(UseCase|Info|Result)\\.java"))
            .toList(),
        imported ->
            imported.equals("org.sopt.member.domain.Member")
                || imported.equals("org.sopt.post.domain.Post")
                || imported.equals("org.sopt.post.domain.PostLike"));
  }

  private final ThreadLocal<String> scannedDomain = new ThreadLocal<>();

  private String currentDomain() {
    return scannedDomain.get();
  }

  private void assertNoImports(List<Path> files, java.util.function.Predicate<String> forbidden)
      throws IOException {
    List<String> violations = new ArrayList<>();
    for (Path file : files) {
      String relative = SOURCE_ROOT.relativize(file).toString();
      scannedDomain.set(relative.substring(0, relative.indexOf('/')));
      var matcher = IMPORT.matcher(Files.readString(file));
      while (matcher.find()) {
        if (forbidden.test(matcher.group(1))) {
          violations.add(relative + " -> " + matcher.group(1));
        }
      }
    }
    scannedDomain.remove();
    assertThat(violations).isEmpty();
  }

  private List<Path> filesIn(String... directories) throws IOException {
    List<Path> files = new ArrayList<>();
    for (String directory : directories) {
      try (Stream<Path> paths = Files.walk(SOURCE_ROOT.resolve(directory))) {
        paths.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
      }
    }
    return files;
  }
}
