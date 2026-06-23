package dev.miningplus.mining;

public record ArtifactSalvageResult(
        Status status,
        long artifactsSalvaged,
        long fragmentsAwarded
) {
    public enum Status {
        SUCCESS,
        DISABLED,
        NO_ARTIFACTS
    }
}
