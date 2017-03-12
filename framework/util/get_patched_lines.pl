#!/usr/bin/env perl
#
#-------------------------------------------------------------------------------
# Copyright (c) 2014-2017 René Just, Darioush Jalali, and Defects4J contributors.
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

get_patched_lines.pl -- Determine the lines modified by the bug patch.

=head1 SYNOPSIS

get_patched_lines.pl -p project_id -v version_id

=head1 OPTIONS

=over 4

=item B<-p C<project_id>>

The id of the project.

=item B<-v C<version_id>>

The id of the project version.

=back

=cut

use warnings;
use strict;
use FindBin;
use File::Basename;
use Cwd qw(abs_path);
use Getopt::Std;

use lib abs_path("$FindBin::Bin/../core");
use Constants;
use Project;

#
# Issue usage message and quit
#
sub _usage {
    die "usage: $0 -p project_id -v version_id"
}

my %cmd_opts;
getopts('p:v:', \%cmd_opts) or _usage();

_usage() unless defined $cmd_opts{p} and defined $cmd_opts{v};

my $PID = $cmd_opts{p};
my $VID = $cmd_opts{v};
# Check format of target version id
$VID =~ /^(\d+)$/ or die "Wrong version id format: $VID -- expected: (\\d+)!";

my $patch_dir = "$SCRIPT_DIR/projects/$PID/patches";
my $src_patch = "$patch_dir/${VID}.src.patch";

my $TMP_DIR = Utils::get_tmp_dir();
system("mkdir -p $TMP_DIR");

# Set up project
my $project = Project::create_project($PID);
my $src_path = $project->src_dir("${VID}f");

##
# fixed
$project->{prog_root} = "$TMP_DIR/fixed";
$project->checkout_vid("${VID}f");

##
# buggy
$project->{prog_root} = "$TMP_DIR/buggy";
$project->checkout_vid("${VID}f");
$project->apply_patch("$TMP_DIR/buggy", $src_patch) or die "Cannot apply patch";

my $MOD_CLASSES = "$SCRIPT_DIR/projects/$PID/modified_classes/$VID.src";
open(LIST, "<$MOD_CLASSES") or die "Could not open list of classes $MOD_CLASSES: $!";
my @classes = <LIST>;
close(LIST);

foreach my $class (@classes) {
    chomp($class);
    my $file = $class;
    $file =~ s/\./\//g;
    $file .= ".java";
   
    # Buggy and fixed versions to compare
    my $buggy = "buggy/$src_path/$file";
    my $fixed = "fixed/$src_path/$file";

    my $diff = "diff --unchanged-line-format='' --old-line-format=\"$file,buggy,%dn%c'\12'\" --new-line-format=\"$file,fixed,%dn%c'\12'\" -w -B $buggy $fixed";

    # Determine modified and added lines in fixed version
    system("cd $TMP_DIR; $diff");
}

# Remove temporary directory
system("rm -rf $TMP_DIR");
