package net.caffeinemc.mods.sodium.client.gl.shader;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderParser {
    public record ParsedShader(String src, String[] includeIds) {
    }

    public static ParsedShader parseShader(String src, ShaderConstants constants) {
        var parser = new ShaderParser();
        parser.parseShader("_root", src);
        parser.prependDefineStrings(constants);

        return parser.finish();
    }

    private final Object2IntMap<String> includeIds = new Object2IntArrayMap<>();
    private final List<String> lines = new LinkedList<>();

    private ShaderParser() {
    }

    public void parseShader(String name, String src) {
        String line;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new StringReader(src))) {
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.startsWith("#version")) {
                    this.lines.add(line);
                    this.lines.add(lineDirectiveFor(name, lineNumber));
                } else if (line.startsWith("#import")) {
                    // add the original import statement as a comment for reference
                    this.lines.add("// START " + line);

                    processImport(line);

                    // reset the line directive to the current file
                    this.lines.add("// END " + line);
                    this.lines.add(lineDirectiveFor(name, lineNumber));
                } else {
                    this.lines.add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader sources", e);
        }
    }

    private String lineDirectiveFor(String name, int line) {
        int idNumber;
        if (!this.includeIds.containsKey(name)) {
            idNumber = this.includeIds.size();
            this.includeIds.put(name, idNumber);
        } else {
            idNumber = this.includeIds.getInt(name);
        }
        return "#line " + (line + 1) + " " + idNumber;
    }

    private void processImport(String line) {
        ResourceLocation name = parseImport(line);

        // mark the start of the imported file
        var nameString = name.toString();
        this.lines.add(lineDirectiveFor(nameString, 0));

        parseShader(nameString, ShaderLoader.getShaderSource(name));
    }

    private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<namespace>.*):(?<path>.*)>");

    private ResourceLocation parseImport(String line) {
        Matcher matcher = IMPORT_PATTERN.matcher(line);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed import statement (expected format: " + IMPORT_PATTERN + ")");
        }

        String namespace = matcher.group("namespace");
        String path = matcher.group("path");

        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private void prependDefineStrings(ShaderConstants constants) {
        this.lines.addAll(1, constants.getDefineStrings());
    }

    private ParsedShader finish() {
        // convert include id map to a list ordered by id
        var includeIds = new String[this.includeIds.size()];
        this.includeIds.forEach((name, id) -> {
            includeIds[id] = name;
        });

        return new ParsedShader(String.join("\n", this.lines), includeIds);
    }
}
