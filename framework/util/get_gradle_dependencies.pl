#!/usr/bin/env perl
#
#-------------------------------------------------------------------------------
# Copyright (c) 2014-2018 René Just, Darioush Jalali, and Defects4J contributors.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#-------------------------------------------------------------------------------

=pod

=head1 NAME

get_gradle_dependencies.pl -- obtain a list of all gradle versions used in the
entire history of a particular project and collect all gradle dependencies.

=head1 SYNOPSIS

  get_gradle_dependencies.pl -p project_id

=head1 DESCRIPTION

Extract all references to gradle distributions from the project's version
control history and collect all dependencies.

B<TODO: This script currently expects the repository to be a git repository!>

=head1 OPTIONS

=over 4

=item -p C<project_id>

The id of the project for which the list of gradle versions and dependencies are
extracted.

=back

=cut
use warnings;
use strict;

use FindBin;
use File::Basename;
use Cwd qw(abs_path);
use Getopt::Std;
use Pod::Usage;

use lib abs_path("$FindBin::Bin/../core");
use Constants;
use Project;

#
# Process arguments and issue usage message if necessary.
#
my %cmd_opts;
getopts('p:', \%cmd_opts) or pod2usage(1);

pod2usage(1) unless defined $cmd_opts{p};

my $PID = $cmd_opts{p};

# Set up project
my $project = Project::create_project($PID);
my $repo = $project->{_vcs}->{repo};
my $cmd = "git -C $repo log -p -- gradle/wrapper/gradle-wrapper.properties | grep distributionUrl";

# Parse the vcs history and grep for distribution urls
my $log;
Utils::exec_cmd($cmd, "Obtaining all gradle dependencies", \$log);

# Process the list and remove duplicates
my %all_deps;
foreach (split(/\n/, $log)) {
    /[+-]distributionUrl=(.+)/ or die "Unexpected line extracted: $_!";
    my $url = $1;
    $url =~ s/\\:/:/g;
    $all_deps{$url} = 1;
}

# Print the ordered list of dependencies to STDOUT
print(join("\n", sort(keys(%all_deps))), "\n");

# Collect all dependencies of a particular project

my $TMP_DIR = Utils::get_tmp_dir();
if (system("mkdir -p $TMP_DIR") != 0) {
    die "Could not create $TMP_DIR directory";
}

my $GRADLE_DEPS_DIR = "$TMP_DIR/deps";
my $GRADLE_DEPS_ZIP = "$BUILD_SYSTEMS_LIB_DIR/gradle/defects4j-gradle-deps.zip";
if (-e "$GRADLE_DEPS_ZIP") {
    # Unzip existing cache so that can be updated with new dependencies
    if (system("unzip -q -u $GRADLE_DEPS_ZIP -d $TMP_DIR") != 0) {
        die "Could not unzip $GRADLE_DEPS_ZIP";
    }
} else {
    if (system("mkdir -p $GRADLE_DEPS_DIR") != 0) {
        die "Could not create $GRADLE_DEPS_DIR directory";
    }
}

my @ids = $project->get_version_ids();
foreach my $bid (@ids) {
    # Checkout a project version
    my $vid = "${bid}f";
    $project->{prog_root} = "$TMP_DIR/$PID-$vid";
    $project->checkout_vid($vid) or die "Could not checkout $PID-${vid}";

    # Compile the project using the gradle wrapper, if it exits (this step
    # should force the download of all dependencies to a local gradle directory)
#    if (! -e "$project->{prog_root}/gradlew") {
#        next;
#    }
#    my $log = "";
#    my $cmd = "export GRADLE_USER_HOME=$project->{prog_root}/$GRADLE_LOCAL_HOME_DIR && \
#               cd $project->{prog_root} && \
#               ./gradlew -xtestClasses -xtest classes && \
#               ./gradlew -xtest testClasses && \
#               ./gradlew --stop";
#    Utils::exec_cmd($cmd, "Compiling the project with gradle", \$log);
    $project->compile() or die "Could not compile source code";
    $project->compile_tests() or die "Could not compile test suites";

    my $gradle_caches_dir = "$project->{prog_root}/$GRADLE_LOCAL_HOME_DIR/caches/modules-2/files-2.1";
    if (! -d $gradle_caches_dir) {
        next;
    }

    # Collect all dependencies

    # Convert pom and jar files from, e.g.,
    # $gradle_caches_dir/org.ow2.asm/asm/5.0.4/b4b92f4b84715dec57de734ff4c3098aa6904d06/asm-5.0.4.pom
    # $gradle_caches_dir/org.ow2.asm/asm/5.0.4/da08b8cce7bbf903602a25a3a163ae252435795/asm-5.0.4.jar
    # to
    # $GRADLE_DEPS_DIR/org/ow2/asm/asm/5.0.4/asm-5.0.4.pom
    # $GRADLE_DEPS_DIR/org/ow2/asm/asm/5.0.4/asm-5.0.4.jar
    $log = "";
    $cmd = "cd $gradle_caches_dir && \
            find . -type f | while read -r f; do \
                d=\$(dirname \$f) \
                mv \"\$f\" \"\$d/../\" \
            done \
            for d in \$(find . -mindepth 1 -maxdepth 1 -type d -printf '%f\\n'); do \
                artifact_dir=$GRADLE_DEPS_DIR/\$(echo \$d | tr '.' '/') \
                mkdir -p \$artifact_dir && \
                (cd \$d && cp -u -R . \$artifact_dir) \
            done";
    Utils::exec_cmd($cmd, "Collecting all project dependencies", \$log);

    # Remove checkout dir
    if (system("rm -rf $project->{prog_root}") != 0) {
        die "Could not remove $project->{prog_root}";
    }
}

# Zip gradle dependencies
if (system("cd $TMP_DIR && zip -q -r $GRADLE_DEPS_ZIP deps") != 0) {
    die "Could not zip $TMP_DIR/deps";
}

# Clean up
system("rm -rf $TMP_DIR");
