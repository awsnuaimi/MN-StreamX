#pragma once
#include "common/io_item.h"
#include <cstdint>
#include <fstream>
#include <limits>
#include <stdexcept>
#include <string>
#include <vector>
namespace marian { namespace io {
// Read metadata without dequantizing every weight. All extents are checked.
inline Item mnxReadMetadata(const std::string& path, const std::string& name) {
    struct Entry { uint64_t nameBytes, type, dimensions, dataBytes; };
    std::ifstream input(path, std::ios::binary | std::ios::ate);
    if (!input || input.tellg() < 16) throw std::runtime_error("Truncated model metadata");
    const auto size = static_cast<uint64_t>(input.tellg()); uint64_t position = 0;
    auto read = [&](void* output, uint64_t bytes) {
        if (bytes > size - position || bytes > static_cast<uint64_t>(std::numeric_limits<std::streamsize>::max())) throw std::runtime_error("Invalid metadata extent");
        input.read(static_cast<char*>(output), static_cast<std::streamsize>(bytes));
        if (!input) throw std::runtime_error("Cannot read metadata"); position += bytes;
    };
    auto skip = [&](uint64_t bytes) {
        if (bytes > size - position) throw std::runtime_error("Invalid metadata offset");
        position += bytes; input.seekg(static_cast<std::streamoff>(position));
        if (!input) throw std::runtime_error("Cannot seek metadata");
    };
    input.seekg(0); uint64_t version = 0, count = 0;
    read(&version, sizeof(version)); read(&count, sizeof(count));
    if (version != 1 || count == 0 || count > 4096) throw std::runtime_error("Unsupported model header");
    std::vector<Entry> entries(static_cast<size_t>(count)); read(entries.data(), count * sizeof(Entry));
    size_t selected = entries.size();
    for (size_t i = 0; i < entries.size(); ++i) {
        const auto& entry = entries[i];
        if (entry.nameBytes == 0 || entry.nameBytes > 1024 || entry.dimensions > 8) throw std::runtime_error("Invalid tensor header");
        std::string value(static_cast<size_t>(entry.nameBytes), '\0'); read(&value[0], entry.nameBytes);
        if (value.back() != '\0') throw std::runtime_error("Invalid tensor name");
        value.pop_back(); if (value == name) selected = i;
    }
    Item result;
    for (size_t i = 0; i < entries.size(); ++i) {
        const auto dimensions = entries[i].dimensions;
        if (i == selected) {
            result.name = name; result.type = static_cast<Type>(entries[i].type); result.shape.resize(static_cast<size_t>(dimensions));
            std::vector<int32_t> shape(static_cast<size_t>(dimensions)); read(shape.data(), dimensions * sizeof(int32_t));
            for (size_t j = 0; j < shape.size(); ++j) {
                if (shape[j] < 0) throw std::runtime_error("Invalid tensor shape");
                result.shape.set(static_cast<int>(j), shape[j]);
            }
        } else skip(dimensions * sizeof(int32_t));
    }
    uint64_t padding = 0; read(&padding, sizeof(padding)); skip(padding);
    for (size_t i = 0; i < entries.size(); ++i) {
        const auto bytes = entries[i].dataBytes;
        if (i == selected) {
            if (bytes > 65536) throw std::runtime_error("Metadata exceeds limit");
            result.bytes->resize(static_cast<size_t>(bytes)); read(result.bytes->data(), bytes); return result;
        }
        skip(bytes);
    }
    return result;
}
} }