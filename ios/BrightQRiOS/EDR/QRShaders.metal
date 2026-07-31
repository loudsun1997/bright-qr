#include <metal_stdlib>
using namespace metal;

struct VertexOutput {
    float4 position [[position]];
};

struct EDRUniforms {
    uint4 geometry; // drawable width, drawable height, matrix size, quiet zone
    uint4 layout;   // pixels/module, render size, origin x, origin y
    float4 values;  // maximum EDR white, mode, reserved, reserved
};

vertex VertexOutput fullScreenVertex(uint vertexID [[vertex_id]]) {
    constexpr float2 positions[] = {
        float2(-1.0, -1.0),
        float2( 3.0, -1.0),
        float2(-1.0,  3.0)
    };

    VertexOutput output;
    output.position = float4(positions[vertexID], 0.0, 1.0);
    return output;
}

fragment float4 edrFragment(
    VertexOutput input [[stage_in]],
    constant EDRUniforms &uniforms [[buffer(0)]],
    device const uint *modules [[buffer(1)]]
) {
    const uint2 pixel = uint2(input.position.xy);
    const float maximumWhite = max(1.0, uniforms.values.x);
    const uint mode = uint(uniforms.values.y);

    if (mode == 0) {
        return float4(maximumWhite, maximumWhite, maximumWhite, 1.0);
    }

    if (mode == 1) {
        const uint shortestSide = max(1u, min(uniforms.geometry.x, uniforms.geometry.y));
        const uint cellSize = max(1u, shortestSide / 8u);
        const bool black = (((pixel.x / cellSize) + (pixel.y / cellSize)) & 1u) == 0u;
        return black
            ? float4(0.0, 0.0, 0.0, 1.0)
            : float4(maximumWhite, maximumWhite, maximumWhite, 1.0);
    }

    const uint pixelsPerModule = max(1u, uniforms.layout.x);
    const uint renderSize = uniforms.layout.y;
    const uint2 origin = uniforms.layout.zw;

    // Any integer pixels left around the centered matrix extend the bright quiet
    // zone. They never cause a fractionally scaled QR module.
    if (pixel.x < origin.x || pixel.y < origin.y ||
        pixel.x >= origin.x + renderSize || pixel.y >= origin.y + renderSize) {
        return float4(maximumWhite, maximumWhite, maximumWhite, 1.0);
    }

    const uint2 localPixel = pixel - origin;
    const uint2 displayModule = localPixel / pixelsPerModule;
    const uint quietZone = uniforms.geometry.w;
    const uint matrixSize = uniforms.geometry.z;

    const bool isQuietZone =
        displayModule.x < quietZone ||
        displayModule.y < quietZone ||
        displayModule.x >= quietZone + matrixSize ||
        displayModule.y >= quietZone + matrixSize;

    if (isQuietZone) {
        return float4(maximumWhite, maximumWhite, maximumWhite, 1.0);
    }

    const uint2 matrixCoordinate = displayModule - quietZone;
    const uint moduleIndex = (matrixCoordinate.y * matrixSize) + matrixCoordinate.x;
    const bool black = modules[moduleIndex] != 0u;
    return black
        ? float4(0.0, 0.0, 0.0, 1.0)
        : float4(maximumWhite, maximumWhite, maximumWhite, 1.0);
}
