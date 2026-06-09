package dev.shaaf.gbuilder.cli;

import dev.shaaf.gbuilder.lang.ParserBackend;
import picocli.CommandLine;

public class ParserBackendConverter implements CommandLine.ITypeConverter<ParserBackend> {

    @Override
    public ParserBackend convert(String value) {
        return ParserBackend.fromCliValue(value);
    }
}
