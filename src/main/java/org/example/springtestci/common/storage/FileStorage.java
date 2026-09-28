package org.example.springtestci.common.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

public interface FileStorage {

  String store(String originalFilename, InputStream content) throws IOException;

  Resource load(String key) throws IOException;

  void delete(String key) throws IOException;
}
