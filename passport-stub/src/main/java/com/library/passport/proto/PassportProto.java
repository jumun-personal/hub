package com.library.passport.proto;

public final class PassportProto {
    private PassportProto() {
    }

    public static final class Passport {
        private final long userId;
        private final String belong;
        private final String role;
        private final String name;

        private Passport(Builder builder) {
            this.userId = builder.userId;
            this.belong = builder.belong;
            this.role = builder.role;
            this.name = builder.name;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public long getUserId() {
            return userId;
        }

        public String getBelong() {
            return belong;
        }

        public String getRole() {
            return role;
        }

        public String getName() {
            return name;
        }

        public static final class Builder {
            private long userId;
            private String belong = "";
            private String role = "";
            private String name = "";

            private Builder() {
            }

            public Builder setUserId(long userId) {
                this.userId = userId;
                return this;
            }

            public Builder setBelong(String belong) {
                this.belong = belong;
                return this;
            }

            public Builder setRole(String role) {
                this.role = role;
                return this;
            }

            public Builder setName(String name) {
                this.name = name;
                return this;
            }

            public Passport build() {
                return new Passport(this);
            }
        }
    }
}
