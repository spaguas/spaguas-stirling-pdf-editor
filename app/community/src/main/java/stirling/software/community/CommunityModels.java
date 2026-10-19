package stirling.software.community;

/** Contracts for the independent Community identity API; password hashes never enter responses. */
public final class CommunityModels {
    private CommunityModels() {}

    public enum Role {
        ADMIN,
        USER
    }

    public enum MemberRole {
        OWNER,
        MEMBER
    }

    public record Account(
            String id, String username, Role role, boolean enabled, boolean mustChangePassword) {}

    public record Team(
            String id,
            String name,
            boolean canProcessPdf,
            boolean membersCanViewDirectory,
            MemberRole membership,
            int memberCount) {}

    public record Member(Account account, MemberRole role) {}

    public record Session(boolean authenticated, Account account, boolean canProcessPdf) {}

    public record CreateAccount(String username, String password, Role role, String teamId) {
        @Override
        public String toString() {
            return "CreateAccount[credentials redacted]";
        }
    }

    public record UpdateAccount(Role role, boolean enabled) {}

    public record ResetPassword(String password) {
        @Override
        public String toString() {
            return "ResetPassword[redacted]";
        }
    }

    public record ChangePassword(String currentPassword, String newPassword) {
        @Override
        public String toString() {
            return "ChangePassword[redacted]";
        }
    }

    public record TeamInput(String name, boolean canProcessPdf, boolean membersCanViewDirectory) {}

    public record MembershipInput(String username, MemberRole role) {}
}
