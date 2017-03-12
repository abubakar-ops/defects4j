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

run_bug_covered.pl -- Determine whether a test suite fully/partially covers a bug.
B<Temporary script that will be merged into the code coverage analysis>

=head1 SYNOPSIS

run_bug_covered.pl -p project_id -d suite_dir -o out_dir [-v version_id]

=head1 OPTIONS

=over 4

=item B<-p C<project_id>> 

The id of the project for which the coverage analysis is performed.

=item B<-d F<suite_dir>> 

The directory that contains the test suite archives.

=item B<-o F<out_dir>> 

The output directory for the coverage results -- this DB directory also has to 
contain the cobertura coverage log files (xml).

=item B<-v C<version_id>> 

Only perform the coverage analysis for this version id (optional). Per default all
suitable version ids are considered. 

=back

=head1 DESCRIPTION

Determines whether a test suite fully/partially covers a bug. For each provided 
test suite (i.e., each test suite archive in F<suite_dir>), this script analyzes
the test suite's coverage data and determines whether it fully/partially covers
the lines modified by the bug fix.

The results of this coverage analysis are stored in the database table 
F<"out_dir"/$TAB_BUG_COVERED>. 

=head2 Test Suites

All test suites in C<suite_dir> have to be provided as an archive that conforms
to the following naming convention: 

B<C<project_id>-C<version_id>-C<test_suite_src>[.C<test_id>].tar.bz2>

Note that the C<test_id> is optional -- the default is 1.

Examples:

=over 4

=item Lang-11f-randoop.1.tar.bz2 (equal to Lang-1-randoop.tar.bz2)

=item Lang-11b-randoop.2.tar.bz2

=item Lang-12b-evosuite-weakmutation.1.tar.bz2

=item Lang-12f-evosuite-branch.1.tar.bz2

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
use Coverage;
use Project;
use DB;

my %cmd_opts;
getopts('p:d:o:v:', \%cmd_opts) or pod2usage(1);
pod2usage(1) unless defined $cmd_opts{p} and defined $cmd_opts{d} and defined $cmd_opts{o};

my $PID = $cmd_opts{p};
my $VID = $cmd_opts{v};
my $SUITE_DIR = abs_path($cmd_opts{d});
my $OUT_DIR = abs_path($cmd_opts{o});

# Set up project
my $project = Project::create_project($PID);

# Check format of target version id
if (defined $VID) {
    my @ids = $project->get_version_ids();
    $VID =~ /^(\d+)[bf]$/ or die "Wrong version_id format: $VID! Expected: \\d+[bf]";
    # Verify that the bug_id is valid if a version_id is provided (version_id = bug_id + [bf])
    $1 ~~ @ids or die "Version id ($VID) does not exist in project: $PID";
} 



# Cache column names for table bug_covered
my @COLS = DB::get_tab_columns($TAB_BUG_COVERED) or die "Cannot obtain table columns!";

# hash all test suites matching the given project_id, using the following mapping:
# version_id -> suite_src -> test_id -> "file_name"
my %test_suites;
my $count = 0;
opendir(DIR, $SUITE_DIR) or die "Cannot open directory: $SUITE_DIR!";
my @entries = readdir(DIR);
closedir(DIR);
foreach (@entries) {
    next unless /^$PID-(\d+[bf])-([^\.]+)(\.(\d+))?.tar.bz2$/;
    my $vid = $1;
    my $suite_src = "$2";
    my $test_id = $4 // 1;

    # Only hash test suites for target version id, if provided
    next if defined $VID and $vid ne $VID;

    # Init hash if necessary
    $test_suites{$vid} = {} unless defined $test_suites{$vid};
    $test_suites{$vid}->{$suite_src} = {} unless defined $test_suites{$vid}->{$suite_src};
    
    # Save archive name for current test id
    $test_suites{$vid}->{$suite_src}->{$test_id}=$_;

    ++$count;
}
print("Found $count test suite archive(s)\n");

# Get database handle for result table
my $dbh_out = DB::get_db_handle($TAB_BUG_COVERED, $OUT_DIR);

my $sth = $dbh_out->prepare("SELECT * FROM $TAB_BUG_COVERED WHERE $PROJECT=? AND $TEST_SUITE=? AND $ID=? AND $TEST_ID=?") 
    or die $dbh_out->errstr;

# Iterate over all version ids
foreach my $vid (keys %test_suites) {
    $vid =~ /^(\d+)[bf]$/ or die "Unexpected version_id format: $vid";
    my $bid = $1;

    # Iterate over all test suite sources (test data generation tools)
    foreach my $suite_src (keys %{$test_suites{$vid}}) {
        
        # Iterate over all test suites for this source
        foreach my $test_id (keys %{$test_suites{$vid}->{$suite_src}}) {
            my $archive = $test_suites{$vid}->{$suite_src}->{$test_id};
            
            # Skip existing entries
            $sth->execute($PID, $suite_src, $vid, $test_id);
            if ($sth->rows !=0) {
                print(" - Skipping $archive since results already exist in database!\n");
                next;
            }

            # Determine coverage on buggy version
            my $buggy = Coverage::parse_xml_log($PID, "${bid}b", $suite_src, $test_id, $OUT_DIR);
            # Determine coverage on fixed version
            my $fixed = Coverage::parse_xml_log($PID, "${bid}f", $suite_src, $test_id, $OUT_DIR);
            # Determine lines modified by bug fix
            my $modified = _parse_patch_diff($bid);

            # Determine whether bug is fully, partially, or not at all covered
            my $cov_buggy = _get_coverage($buggy, $modified, "buggy");
            my $cov_fixed = _get_coverage($fixed, $modified, "fixed");

            # Add information about test suite to hash that holds the coverage information
            my %data = (
                $PROJECT       => $PID,
                $ID            => $vid,
                $TEST_SUITE    => $suite_src,
                $TEST_ID       => $test_id,
                $BUGGY_COVERED => $cov_buggy,
                $FIXED_COVERED => $cov_fixed,
            );
            _insert_row(\%data);
        }
    }
}

#
# Determine covered and uncovered lines, and compare results with modified lines
# of bug patch.
#
sub _get_coverage {
    my ($coverage, $modified, $type) = @_;

    return undef unless defined $coverage;
   
    my $hit  = 0; 
    my $miss = 0;
    my $lines= 0;
    my $full = 1;
    foreach my $class(keys %{$modified}) {
        $lines += scalar(keys %{$modified->{$class}->{$type}});
        # Check whether the test did cover this file at all
        if (defined $coverage->{$class}) {
            foreach my $line (keys %{$modified->{$class}->{$type}}) {
                if (defined $coverage->{$class}->{"uncovered"}->{$line}) {
                    ++$miss;
                    $full = 0;
                }
                ++$hit  if defined $coverage->{$class}->{"covered"}->{$line};
            }
        } else {
            ++$miss;
            $full=0;
        }
    }
    # No modified lines to cover -> report n/a
    if ($lines == 0) {
        return "n/a";
    } elsif ($miss>0 and $hit==0) {
        return "no";
    } elsif ($miss>0 and $hit>0) {
        return "partial";
    } elsif (($miss==0 and $hit>0) or $full) {
        return "full";
    } 
    return "undef";
}

#
# Parse patch diff and determine lines modified in buggy and fixed version.
#
sub _parse_patch_diff {
    my ($bid) = @_;
    
    my %modified = ();
    open(IN, "<$SCRIPT_DIR/projects/$PID/patches/$bid.src.diff") or die "Cannot read modified lines: $!";
    while(<IN>) {
        chomp;
        /^([^,]+),(fixed|buggy),(\d+)$/ or die "Wrong format in patch diff: $_";
        my $class = $1;
        my $type  = $2;
        my $line  = $3;

        unless (defined $modified{$class}) {
            $modified{$class} = {};
        }
        unless (defined $modified{$class}->{$type}) {
            $modified{$class}->{$type} = {};
        }
        $modified{$class}->{$type}->{$line} = 1;
    }
    close(IN);
    return \%modified;
}

#
# Insert row into database table.
#
sub _insert_row {
    my ($data) = @_;
    my $dbh = DB::get_db_handle($TAB_BUG_COVERED, $OUT_DIR);
    my @tmp;
    foreach (@COLS) {
        push (@tmp, $dbh->quote((defined $data->{$_} ? $data->{$_} : "-")));
    }
    my $row = join(",", @tmp);
    $dbh->do("INSERT INTO $TAB_BUG_COVERED VALUES ($row)");
}
