/**
 * Jobs Plugin for Bukkit
 * Copyright (C) 2011 Zak Ford <zak.j.ford@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.zford.jobs.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.zford.jobs.Jobs;
import me.zford.jobs.config.ConfigManager;
import me.zford.jobs.dao.JobsDAO;
import me.zford.jobs.dao.JobsDAOData;
import me.zford.jobs.util.ChatColor;

public class JobsPlayer {
    // the player the object belongs to
    private String playername;
    // progression of the player in each job
    private ArrayList<JobProgression> progression = new ArrayList<JobProgression>();
    // display honorific
    private String honorific;
    // player save status
    private volatile boolean isSaved = true;
    // player online status
    private volatile boolean isOnline = false;
    
    // save lock
    public final Object saveLock = new Object();
    
    /**
     * Constructor.
     * Reads data storage and configures itself.
     * @param playername - the player this represents
     * @param dao - the data access object
     */
    public JobsPlayer(String playername) {
        this.playername = playername;
    }
    
    public void loadDAOData(List<JobsDAOData> list) {
        synchronized (saveLock) {
            progression.clear();
            for (JobsDAOData jobdata: list) {
                if (Jobs.getJob(jobdata.getJobName()) != null) {
                    // add the job
                    Job job = Jobs.getJob(jobdata.getJobName());
                    if (job != null) {
                        // create the progression object
                        JobProgression jobProgression = new JobProgression(job, this, jobdata.getLevel(), jobdata.getExperience(), ConfigManager.getJobsConfiguration().getTitleForLevel(jobdata.getLevel()));
                        // calculate the max level
                        
                        // add the progression level.
                        progression.add(jobProgression);
                    }
                }
            }
        }
    }
    
    /**
     * Get the list of job progressions
     * @return the list of job progressions
     */
    public List<JobProgression> getJobProgression() {
        return Collections.unmodifiableList(progression);
    }
    
    /**
     * Get the job progression with the certain job
     * @return the job progression
     */
    public JobProgression getJobProgression(Job job) {
        for (JobProgression prog : progression) {
            if (prog.getJob().equals(job))
                return prog;
        }
        return null;
    }
    
    /**
     * get the player
     * @return the player
     */
    public String getName(){
        return playername;
    }
    
    public String getDisplayHonorific() {
        return honorific;
    }
    
    /**
     * Player joins a job
     * @param job - the job joined
     */
    public boolean joinJob(Job job) {
        synchronized (saveLock) {
            if (!isInJob(job)) {
                progression.add(new JobProgression(job, this, 1, 0.0, ConfigManager.getJobsConfiguration().getTitleForLevel(1)));
                reloadHonorific();
                Jobs.getPermissionHandler().recalculatePermissions(this);
                return true;
            }
            return false;
        }
    }
    
    /**
     * Player leaves a job
     * @param job - the job left
     */
    public boolean leaveJob(Job job) {
        synchronized (saveLock) {
            JobProgression prog = getJobProgression(job);
            progression.remove(prog);
            if (prog != null) {
                reloadHonorific();
                Jobs.getPermissionHandler().recalculatePermissions(this);
                return true;
            }
            return false;
        }
    }
    
    /**
     * Leave all jobs
     * @return on success
     */
    public boolean leaveAllJobs() {
        synchronized (saveLock) {
            progression.clear();
            reloadHonorific();
            Jobs.getPermissionHandler().recalculatePermissions(this);;
            return true;
        }
    }
    
    /**
     * Promotes player in job
     * @param job - the job being promoted
     * @param levels - number of levels to promote
     */
    public void promoteJob(Job job, int levels) {
        synchronized (saveLock) {
            JobProgression prog = getJobProgression(job);
            if (prog == null)
                return;
            if (levels <= 0)
                return;
            int newLevel = prog.getLevel() + levels;
            int maxLevel = job.getMaxLevel();
            if (maxLevel > 0 && newLevel > maxLevel) {
                newLevel = maxLevel;
            }
            setLevel(job, newLevel);
        }
    }
    
    /**
     * Demotes player in job
     * @param job - the job being deomoted
     * @param levels - number of levels to demote
     */
    public void demoteJob(Job job, int levels) {
        synchronized (saveLock) {
            JobProgression prog = getJobProgression(job);
            if (prog == null)
                return;
            if (levels <= 0)
                return;
            int newLevel = prog.getLevel() - levels;
            if (newLevel < 1) {
                newLevel = 1;
            }
            setLevel(job, newLevel);
        }
    }
    
    /**
     * Sets player to a specific level
     * @param job - the job
     * @param level - the level
     */
    private void setLevel(Job job, int level) {
        synchronized (saveLock) {
            JobProgression prog = getJobProgression(job);
            if (prog == null)
                return;
    
            if (level != prog.getLevel()) {
                prog.setLevel(level);
                reloadHonorific();
                Jobs.getPermissionHandler().recalculatePermissions(this);;
            }
        }
    }
    
    /**
     * Player leaves a job
     * @param oldjob - the old job
     * @param newjob - the new job
     */
    public boolean transferJob(Job oldjob, Job newjob) {
        synchronized (saveLock) {
            if (!isInJob(newjob)) {
                for (JobProgression prog : progression) {
                    if (!prog.getJob().equals(oldjob))
                        continue;
                    
                    prog.setJob(newjob);
                    if (newjob.getMaxLevel() > 0 && prog.getLevel() > newjob.getMaxLevel()) {
                        prog.setLevel(newjob.getMaxLevel());
                    }
                    reloadHonorific();
                    Jobs.getPermissionHandler().recalculatePermissions(this);;
                    return true;
                }
            }
            return false;
        }
    }
    
    /**
     * Checks if the player is in this job.
     * @param job - the job
     * @return true - they are in the job
     * @return false - they are not in the job
     */
    public boolean isInJob(Job job) {
        for (JobProgression prog : progression) {
            if (prog.getJob().equals(job))
                return true;
        }
        return false;
    }
    
    /**
     * Function that reloads your honorific
     */
    public void reloadHonorific() {
        StringBuilder builder = new StringBuilder();
        int numJobs = progression.size();
        boolean gotTitle = false;
        for (JobProgression prog: progression) {
            DisplayMethod method = prog.getJob().getDisplayMethod();
            
            if (method.equals(DisplayMethod.NONE))
                continue;
            
            if (gotTitle) {
                builder.append(" ");
                gotTitle = false;
            }
            
            if (numJobs == 1) {
                if (method.equals(DisplayMethod.FULL) || method.equals(DisplayMethod.TITLE)) {
                    if (prog.getTitle() != null) {
                        builder.append(prog.getTitle().getChatColor() + prog.getTitle().getName() + ChatColor.WHITE);
                        gotTitle = true;
                    }
                }
                
                if(method.equals(DisplayMethod.FULL) || method.equals(DisplayMethod.JOB)) {
                    if (gotTitle) {
                        builder.append(" ");
                    }
                    builder.append(prog.getJob().getChatColor() + prog.getJob().getName() + ChatColor.WHITE);
                    gotTitle = true;
                }
            }
            
            if (numJobs > 1 && (method.equals(DisplayMethod.FULL) || method.equals(DisplayMethod.TITLE)) ||
                    method.equals(DisplayMethod.SHORT_FULL) ||
                    method.equals(DisplayMethod.SHORT_TITLE)) {
                // add title to honorific
                if (prog.getTitle() != null) {
                    builder.append(prog.getTitle().getChatColor() + prog.getTitle().getShortName() + ChatColor.WHITE);
                    gotTitle = true;
                }
            }
            
            if (numJobs > 1 && (method.equals(DisplayMethod.FULL) || method.equals(DisplayMethod.JOB)) ||
                    method.equals(DisplayMethod.SHORT_FULL) || 
                    method.equals(DisplayMethod.SHORT_JOB)) {
                builder.append(prog.getJob().getChatColor() + prog.getJob().getShortName() + ChatColor.WHITE);
                gotTitle = true;
            }
        }
        
        honorific = builder.toString().trim();
    }
    
    /**
     * Performs player save
     * @param dao
     */
    public void save(JobsDAO dao) {
        synchronized (saveLock) {
            if (!isSaved()) {
                dao.save(this);
                setSaved(true);
            }
        }
    }
    
    /**
     * Perform connect
     */
    public void onConnect() {
        isOnline = true;
    }
    
    /**
     * Perform disconnect
     * 
     */
    public void onDisconnect() {
        isOnline = false;
    }
    
    /**
     * Whether or not player is online
     * @return true if online, otherwise false
     */
    public boolean isOnline() {
        return isOnline;
    }
    
    public boolean isSaved() {
        return isSaved;
    }
    
    public void setSaved(boolean value) {
        isSaved = value;
    }
}
